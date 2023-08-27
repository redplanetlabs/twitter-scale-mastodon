package com.rpl.mastodon;

import clojure.lang.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpl.mastodon.data.*;
import org.apache.commons.text.StringEscapeUtils;
import org.bouncycastle.util.io.pem.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class MastodonWebHelpers {

    static {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    public static String PUBLIC_URL = "https://www.w3.org/ns/activitystreams#Public";

    public static class SigningKeyPair {
        public String publicKey;
        public String privateKey;

        public SigningKeyPair(String publicKey, String privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }
    }

    public static SigningKeyPair generateKeys() throws NoSuchProviderException, NoSuchAlgorithmException, IOException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "BC");
        KeyPair pair = generator.generateKeyPair();
        Key privKey = pair.getPrivate();
        Key pubKey = pair.getPublic();
        StringWriter privStringWriter = new StringWriter();
        try (PemWriter privWriter = new PemWriter(privStringWriter)) {
            privWriter.writeObject(new PemObject("PRIVATE KEY", privKey.getEncoded()));
        }
        StringWriter pubStringWriter = new StringWriter();
        try (PemWriter pubWriter = new PemWriter(pubStringWriter)) {
            pubWriter.writeObject(new PemObject("PUBLIC KEY", pubKey.getEncoded()));
        }
        return new SigningKeyPair(pubStringWriter.toString(), privStringWriter.toString());
    }

    public static String sign(String privateKey, String message) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException, InvalidKeyException, SignatureException {
        try (StringReader strReader = new StringReader(privateKey);
             PemReader pemReader = new PemReader(strReader)) {
            PemObject pemObject = pemReader.readPemObject();
            byte[] content = pemObject.getContent();
            PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(content);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PrivateKey privKey = factory.generatePrivate(privKeySpec);

            Signature rsaSign = Signature.getInstance("SHA256withRSA", "BC");
            rsaSign.initSign(privKey);
            rsaSign.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rsaSign.sign());
        }
    }

    public static boolean verify(String publicKey, String message, String signature) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException, InvalidKeyException, SignatureException {
        try (StringReader strReader = new StringReader(publicKey);
             PemReader pemReader = new PemReader(strReader)) {
            PemObject pemObject = pemReader.readPemObject();
            byte[] content = pemObject.getContent();
            X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(content);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PublicKey pubKey = factory.generatePublic(pubKeySpec);

            Signature rsaVerify = Signature.getInstance("SHA256withRSA", "BC");
            rsaVerify.initVerify(pubKey);
            rsaVerify.update(message.getBytes(StandardCharsets.UTF_8));
            return rsaVerify.verify(Base64.getDecoder().decode(signature));
        }
    }

    public static String getKeyId(Account account) {
        return String.format("acct:%s@%s", account.name, MastodonConfig.API_DOMAIN);
    }

    public static IPersistentMap signHeaders(String path, String body, Account account) {
        if (!account.content.isSetLocal()) throw new RuntimeException("Account is not local");

        IPersistentMap headers = PersistentHashMap.EMPTY;

        final MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        md.update(body.getBytes(StandardCharsets.UTF_8));
        byte[] digest = md.digest();
        String encodedDigest = "SHA-256=" + Base64.getEncoder().encodeToString(digest);
        headers = headers.assoc("Digest", encodedDigest);

        String date = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH).withZone(ZoneId.of("GMT")).format(Instant.now());
        headers = headers.assoc("Date", date);

        List<String> messageParts = Arrays.asList("(request-target): post " + path, "date: " + date, "digest: " + encodedDigest);
        String message = String.join("\n", messageParts);
        String keyId = getKeyId(account);
        String signature = null;
        try {
            signature = sign(account.content.getLocal().privateKey, message);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException | NoSuchProviderException | InvalidKeyException | SignatureException e) {
            throw new RuntimeException(e);
        }
        headers = headers.assoc("Signature", String.format("keyId=\"%s\",headers=\"(request-target) date digest\",signature=\"%s\"", keyId, signature));
        return headers;
    }

    public static AccountWithId verifyHeaders(Set<Map.Entry<String, List<String>>> headers, String path, String body, AccountWithId accountWithId) {
        if (!accountWithId.account.content.isSetRemote()) throw new RuntimeException("Account is not remote");
        Optional<String> sigHeader = headers.stream().filter(o -> o.getKey().equals("Signature")).map(o -> o.getValue().get(0)).findFirst();
        if (!sigHeader.isPresent()) throw new RuntimeException("Signature header required");

        Map<String, String> sigHeaderMap = new HashMap<>();
        for (String pairStr : sigHeader.get().split(",")) {
            int idx = pairStr.indexOf('=');
            if (idx == -1)  throw new RuntimeException("Invalid Signature header");
            String key = pairStr.substring(0, idx);
            String val = pairStr.substring(idx+1);
            sigHeaderMap.put(key, val.replace("\"", ""));
        }
        if (!sigHeaderMap.containsKey("keyId") || !sigHeaderMap.containsKey("headers") || !sigHeaderMap.containsKey("signature")) {
            throw new RuntimeException("Signature does not contain required fields");
        }

        String[] headerNames = sigHeaderMap.get("headers").split(" ");
        Set<String> headerSet = new HashSet<>(Arrays.asList(headerNames));
        if (!headerSet.containsAll(Arrays.asList("(request-target)", "date", "digest"))) throw new RuntimeException("Signature headers field does not contain required headers");

        Map<String, String> headerMap = new HashMap<>();
        for (Map.Entry<String, List<String>> header : headers) headerMap.put(header.getKey().toLowerCase(), header.getValue().get(0));
        if (!headerMap.keySet().containsAll(Arrays.asList("date", "digest"))) throw new RuntimeException("Required headers do not exist");

        final MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        md.update(body.getBytes(StandardCharsets.UTF_8));
        byte[] digest = md.digest();
        String encodedDigest = "SHA-256=" + Base64.getEncoder().encodeToString(digest);
        if (!encodedDigest.equals(headerMap.get("digest"))) throw new RuntimeException("Invalid digest");

        List<String> messageParts = new ArrayList<>();
        for (String headerName : headerNames) {
            if (headerName.equals("(request-target)")) messageParts.add(headerName + ": post " + path);
            else messageParts.add(headerName + ": " + headerMap.get(headerName));
        }
        String message = String.join("\n", messageParts);
        final boolean verified;
        try {
            verified = verify(accountWithId.account.publicKey, message, sigHeaderMap.get("signature"));
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException | NoSuchProviderException |
                 InvalidKeyException | SignatureException e) {
            throw new RuntimeException(e);
        }
        if (!verified) throw new RuntimeException("Signature verification failed");
        return accountWithId;
    }

    private static void log(AtomicReference<Long> lastLogRef, Throwable t) {
        lastLogRef.updateAndGet(lastLog -> {
            long ts = System.currentTimeMillis();
            if (ts - lastLog >= 60000) {
                t.printStackTrace();
                return ts;
            }
            return lastLog;
        });
    }

    static final Var require = RT.var("clojure.core", "require");
    static final Var symbol = RT.var("clojure.core", "symbol");

    public static synchronized Var getVar(String ns_name, String fn_name) {
      require.invoke(symbol.invoke(ns_name));
      return RT.var(ns_name, fn_name);
    }

    public static synchronized Object getVarContents(String ns_name, String name) {
      return getVar(ns_name, name).deref();
    }

    public static synchronized IFn getIFn(String ns_name, String fn_name) {
      return (IFn) getVarContents(ns_name, fn_name);
    }

    private static final IFn POST = getIFn("org.httpkit.client", "post");
    private static final AtomicReference<Long> BUILD_REQUEST_LAST_LOG = new AtomicReference<>(0L);
    private static final Keyword ERROR_KEYWORD = Keyword.intern("error");
    private static final Keyword STATUS_KEYWORD = Keyword.intern("status");
    private static final Keyword HEADERS_KEYWORD = Keyword.intern("headers");
    private static final Keyword BODY_KEYWORD = Keyword.intern("body");

    private static void completeAndLogExceptionally(CompletableFuture f, Throwable t) {
      log(BUILD_REQUEST_LAST_LOG, t);
      f.completeExceptionally(t);
    }

    public static CompletableFuture<Boolean> buildRequest(Account account, String url, Object bodyObj) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            final String bodyStr = objectMapper.writeValueAsString(bodyObj);
            final String path = new URI(url).getPath();

            IPersistentMap opts = PersistentHashMap.EMPTY;
            opts = opts.assoc(HEADERS_KEYWORD, signHeaders(path, bodyStr, account).assoc("Content-Type", "application/json"));
            opts = opts.assoc(BODY_KEYWORD, bodyStr);

            CompletableFuture<Boolean> future = new CompletableFuture<>();
            POST.invoke(url, opts, new AFn() {
                @Override
                public Object invoke(Object arg) {
                    if (arg instanceof Map) {
                        Map response = (Map) arg;
                        if (response.containsKey(ERROR_KEYWORD)) {
                            completeAndLogExceptionally(future, new RuntimeException("Error " + response));
                        } else {
                            Object status = response.get(STATUS_KEYWORD);
                            if (status != null && status instanceof Number && ((Number) status).intValue() >= 200 && ((Number) status).intValue() < 300) {
                                future.complete(true);
                            } else {
                                completeAndLogExceptionally(future, new RuntimeException("Status not 200: " + arg));
                            }
                        }
                    } else {
                        completeAndLogExceptionally(future, new RuntimeException("Invalid response type " + arg));
                    }
                    return null;
                }
            });
            return future;
        } catch (Throwable t) {
            CompletableFuture future = new CompletableFuture<>();
            completeAndLogExceptionally(future, t);
            return future;
        }
    }

    public static void addHtml(HashMap map, String originalContent, String receiverUrl) throws MalformedURLException {
        URL url = new URL(receiverUrl);
        String host = url.getHost();
        String content = "";
        for (Token token : Token.parseTokens(originalContent)) {
            String tokenContent = StringEscapeUtils.escapeHtml4(token.content);
            switch (token.kind) {
                case BOUNDARY:
                case WORD:
                    content += tokenContent;
                    break;
                case LINK:
                    content += String.format("<a href=\"%s\">%s</a>", tokenContent, tokenContent);
                    break;
                case HASHTAG: {
                    String href = String.format("https://%s/tags/%s", host, tokenContent);
                    content += String.format("<a href=\"%s\">%s</a>", href, tokenContent);
                    // update the list of tags
                    List<HashMap> tags = (List<HashMap>) map.getOrDefault("tag", new ArrayList<HashMap>());
                    tags.add(new HashMap() {{
                        put("type", "Hashtag");
                        put("href", href);
                        put("name", "#" + tokenContent);
                    }});
                    map.put("tag", tags);
                    break;
                }
                case MENTION: {
                    String href = String.format("https://%s/@%s@%s", host, tokenContent, MastodonConfig.API_DOMAIN);
                    content += String.format("<a href=\"%s\">%s@%s</a>", href, tokenContent, MastodonConfig.API_DOMAIN);
                    // update the list of tags
                    List<HashMap> tags = (List<HashMap>) map.getOrDefault("tag", new ArrayList<HashMap>());
                    tags.add(new HashMap() {{
                        put("type", "Mention");
                        put("href", href);
                        put("name", "@" + tokenContent);
                    }});
                    map.put("tag", tags);
                    break;
                }
                case REMOTE_MENTION: {
                    String[] parts = tokenContent.split("@");
                    if (parts.length == 2 && parts[1].equals(host)) {
                        String href = String.format("https://%s/@%s", host, parts[0]);
                        content += String.format("<a href=\"%s\">%s</a>", href, tokenContent);
                        // update the list of tags
                        List<HashMap> tags = (List<HashMap>) map.getOrDefault("tag", new ArrayList<HashMap>());
                        tags.add(new HashMap() {{
                            put("type", "Mention");
                            put("href", href);
                            put("name", "@" + tokenContent);
                        }});
                        map.put("tag", tags);
                    } else content += tokenContent;
                    break;
                }
            }
        }
        map.put("content", content);
    }

    public static HashMap createMap(String id, String type, Object to, Object cc) {
        HashMap map = new HashMap();
        map.put("id", id);
        map.put("type", type);
        map.put("to", to);
        map.put("cc", cc);
        return map;
    }

    public static HashMap createMap(String id, String type, Object to, Object cc, String actor, Object object) {
        HashMap map = createMap(id, type, to, cc);
        map.put("@context", "https://www.w3.org/ns/activitystreams");
        map.put("actor", actor);
        map.put("object", object);
        return map;
    }

    public static boolean sendRemoteStatus(FanoutAction fanoutAction, Account sender, long statusId, Status status, Status parentStatus, Status boostedStatus, String mainUrl, String inboxUrl) {
        List<String> to = new ArrayList<>();
        List<String> cc = new ArrayList<>();
        StatusVisibility visibility = MastodonHelpers.getStatusVisibility(status);
        String followersUrl = String.format("%s/users/%s/followers", MastodonConfig.API_URL, sender.name);
        if (visibility == StatusVisibility.Public) {
            to.add(PUBLIC_URL);
            cc.add(followersUrl);
        } else if (visibility == StatusVisibility.Unlisted) {
            to.add(followersUrl);
            cc.add(PUBLIC_URL);
        } else if (visibility == StatusVisibility.Private) to.add(followersUrl);
        if (mainUrl != null) to.add(mainUrl);
        if (to.size() == 0) throw new RuntimeException("No receiver for status: " + statusId);

        String id = String.format("%s/users/%s/statuses/%s", MastodonConfig.API_URL, sender.name, MastodonHelpers.serializeStatusPointer(new StatusPointer(status.authorId, statusId)));
        String actorUrl = String.format("%s/users/%s", MastodonConfig.API_URL, sender.name);

        switch (fanoutAction) {
            case Add:
            case Edit:
            {
                String published = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(status.timestamp));

                if (boostedStatus != null) {
                    String objectUrl = boostedStatus.remoteUrl;
                    if (objectUrl == null) {
                        BoostStatusContent statusContent = status.content.getBoost();
                        objectUrl = MastodonConfig.API_URL + "/users/" + sender.name + "/statuses/" + MastodonHelpers.serializeStatusPointer(statusContent.boosted);
                    }
                    HashMap announce = createMap(id, "Announce", to, cc, actorUrl, objectUrl);
                    announce.put("published", published);

                    buildRequest(sender, inboxUrl, announce);
                } else {
                    HashMap note = createMap(id, "Note", to, cc);
                    note.put("published", published);

                    PollContent pollContent;

                    if (status.content.isSetNormal()) {
                        NormalStatusContent content = status.content.getNormal();
                        try {
                            addHtml(note, content.text, inboxUrl);
                        } catch (MalformedURLException e) {
                            throw new RuntimeException(e);
                        }

                        pollContent = status.content.getNormal().pollContent;
                    } else if (status.content.isSetReply()) {
                        ReplyStatusContent content = status.content.getReply();
                        try {
                            addHtml(note, content.text, inboxUrl);
                        } catch (MalformedURLException e) {
                            throw new RuntimeException(e);
                        }
                        if (parentStatus == null) throw new RuntimeException("Couldn't find parent status");
                        String remoteParentUrl = parentStatus.remoteUrl;
                        if (remoteParentUrl == null) {
                            remoteParentUrl = String.format("%s/users/%s/statuses/%s", MastodonConfig.API_URL, sender.name, MastodonHelpers.serializeStatusPointer(content.parent));
                        }
                        note.put("inReplyTo", remoteParentUrl);

                        pollContent = status.content.getReply().pollContent;
                    } else throw new RuntimeException("Unexpected status content type");

                    if (pollContent != null) {
                        note.put("type", "Question");
                        note.put("endTime", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(pollContent.expirationMillis)));
                        List<HashMap> options = pollContent.choices.stream().map(option ->
                                new HashMap(){{
                                    put("type", "Note");
                                    put("name", option);
                                    put("replies", new HashMap(){{
                                        put("type", "Collection");
                                        put("totalItems", 0);
                                    }});
                                }}
                        ).collect(Collectors.toList());
                        note.put(pollContent.multipleChoice ? "anyOf" : "oneOf", options);
                    }

                    final Map body;
                    if (fanoutAction == FanoutAction.Add) body = createMap(id + "#create", "Create", to, cc, actorUrl, note);
                    else if (fanoutAction == FanoutAction.Edit) {
                        String updated = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
                        note.put("updated", updated);
                        body = createMap(id + "#update", "Update", to, cc, actorUrl, note);
                    } else throw new RuntimeException("Unexpected fanout action: " + fanoutAction);

                    buildRequest(sender, inboxUrl, body);
                }
                return true;
            }
            case Remove:
            {
                if (boostedStatus != null) {
                    String objectUrl = boostedStatus.remoteUrl;
                    if (objectUrl == null) {
                        BoostStatusContent statusContent = status.content.getBoost();
                        objectUrl = MastodonConfig.API_URL + "/users/" + sender.name + "/statuses/" + MastodonHelpers.serializeStatusPointer(statusContent.boosted);
                    }

                    HashMap announce = createMap(id, "Announce", to, cc, actorUrl, objectUrl);
                    buildRequest(sender, inboxUrl, createMap(id + "#delete", "Undo", to, cc, actorUrl, announce));
                } else {
                    HashMap tombstone = createMap(id, "Tombstone", to, cc);
                    buildRequest(sender, inboxUrl, createMap(id + "#delete", "Delete", to, cc, actorUrl, tombstone));
                }
                return true;
            }
            default: throw new RuntimeException("Unexpected fanout action: " + fanoutAction);
        }
    }
}
