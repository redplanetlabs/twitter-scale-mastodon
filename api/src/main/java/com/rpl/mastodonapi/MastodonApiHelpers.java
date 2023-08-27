package com.rpl.mastodonapi;

import com.rpl.mastodon.*;
import com.rpl.mastodon.data.*;
import com.rpl.mastodonapi.pojos.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jcodec.api.*;
import org.bouncycastle.util.encoders.Hex;
import org.jcodec.common.io.NIOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.*;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import javax.imageio.ImageIO;
import java.io.*;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.*;
import java.util.AbstractMap.SimpleEntry;

public class MastodonApiHelpers {
    private static final DelegatingPasswordEncoder PASSWORD_ENCODER;

    static {
        HashMap<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        PASSWORD_ENCODER = new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    public static String encodePassword(String password) {
        return PASSWORD_ENCODER.encode(password);
    }

    public static boolean matchesPassword(String password, String passwordHash) {
        return PASSWORD_ENCODER.matches(password, passwordHash);
    }

    public static String randomString(int size) throws NoSuchAlgorithmException {
        byte[] bytes = new byte[size];
        SecureRandom.getInstanceStrong().nextBytes(bytes);
        return Hex.toHexString(bytes);
    }

    private static S3AsyncClient S3_CLIENT = null;
    public static void initS3Client() {
        S3_CLIENT = S3AsyncClient.builder().credentialsProvider(EnvironmentVariableCredentialsProvider.create()).build();
    }

    public static CompletableFuture<PutObjectResponse> uploadToS3(String bucketName, String key, File file) {
        PutObjectRequest objectRequest = PutObjectRequest.builder().bucket(bucketName).key(key).build();
        return S3_CLIENT.putObject(objectRequest, AsyncRequestBody.fromFile(file));
    }

    public static CompletableFuture<GetActivityPubProfile> getProfile(String nameWithHost) {
        String[] parts = nameWithHost.split("@");
        if (parts.length == 2) {
            final String url;
            if (MastodonConfig.API_DOMAIN.equals("localhost")) url = String.format("%s/users/%s", MastodonConfig.API_URL, parts[0]);
            else url = String.format("https://%s/users/%s", parts[1], parts[0]);
            WebClient webClient = WebClient.create(url);
            return webClient.get().uri("").accept(MediaType.APPLICATION_JSON)
                            .exchangeToMono(response -> {
                                if (HttpStatus.Series.SUCCESSFUL == response.statusCode().series()) return response.bodyToMono(GetActivityPubProfile.class);
                                else return Mono.fromFuture(CompletableFuture.completedFuture(null));
                            }).toFuture();
        }
        else return CompletableFuture.completedFuture(null);
    }

    public static <T, O> void setLinkHeader(ServerWebExchange exchange, MastodonApiManager.QueryResults<T, O> queryResults) {
        if (queryResults.linkHeaderParams != null && !queryResults.reachedEnd) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(MastodonConfig.API_URL);
            builder.path(exchange.getRequest().getPath().pathWithinApplication().value());
            // collect the existing query params
            for (Map.Entry<String, List<String>> entry : exchange.getRequest().getQueryParams().entrySet()) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }
            // collect the new params (these will override the existing ones)
            for (SimpleEntry<String, String> entry : queryResults.linkHeaderParams) {
                builder.replaceQueryParam(entry.getKey(), entry.getValue());
            }
            // set the header
            exchange.getResponse().getHeaders().add("Link", String.format("<%s>; rel=\"next\"", builder.toUriString()));
        }
    }

    public static void setStatusLinkHeader(ServerWebExchange exchange, StatusQueryResults statusQueryResults) {
        if (statusQueryResults.isSetLastStatusPointer() && !statusQueryResults.reachedEnd) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(MastodonConfig.API_URL);
            builder.path(exchange.getRequest().getPath().pathWithinApplication().value());
            // collect the existing query params
            for (Map.Entry<String, List<String>> entry : exchange.getRequest().getQueryParams().entrySet()) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }
            // collect the new params (these will override the existing ones)
            builder.replaceQueryParam("max_id", MastodonHelpers.serializeStatusPointer(statusQueryResults.lastStatusPointer));
            // set the header
            exchange.getResponse().getHeaders().add("Link", String.format("<%s>; rel=\"next\"", builder.toUriString()));
        }
    }

    public static boolean isValidFile(String kind, File file) {
        try {
          if ("image".equals(kind)) ImageIO.read(file); // make sure it's a valid image file
          else if ("video".equals(kind)) FrameGrab.createFrameGrab(NIOUtils.readableChannel(file)); // make sure it's a valid video file
          return true;
        } catch (IOException | JCodecException e) {
          return false;
        }
    }

    public static String sanitize(String input, int maxLength) {
        String sanitized = input.trim();
        if (sanitized.length() > maxLength) return sanitized.substring(0, maxLength);
        return sanitized;
    }

    public static String sanitizeField(String input) {
        return sanitize(input, MastodonApiConfig.MAX_FIELD_LENGTH);
    }

    public static boolean isValidURL(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (MalformedURLException | URISyntaxException e) {
            return false;
        }
    }

    public static String remoteMentionToUrl(String remoteMention) {
        String[] parts = remoteMention.split("@");
        if (parts.length == 3 && parts[0].equals("")) {
            String url = "https://" + parts[2] + "/@" + parts[1];
            if (isValidURL(url)) return url;
        }
        return null;
    }

    public static String getStatusResultContentText(StatusResultContent content) {
        if (content.isSetNormal()) return content.getNormal().text;
        else if (content.isSetReply()) return content.getReply().text;
        else if (content.isSetBoost()) return getStatusResultContentText(content.getBoost().status.content);
        return "";
    }

    public static String getStatusContentText(StatusContent content) {
        if (content.isSetNormal()) return content.getNormal().text;
        else if (content.isSetReply()) return content.getReply().text;
        else return "";
    }

    public static StatusVisibility createStatusVisibility(String visibilityStr) {
        if ("public".equals(visibilityStr)) return StatusVisibility.Public;
        else if ("unlisted".equals(visibilityStr)) return StatusVisibility.Unlisted;
        else if ("private".equals(visibilityStr)) return StatusVisibility.Private;
        else if ("direct".equals(visibilityStr)) return StatusVisibility.Direct;
        else throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    public static String createStatusVisibility(StatusVisibility visibility) {
        switch (visibility) {
            case Public: return "public";
            case Unlisted: return "unlisted";
            case Private: return "private";
            case Direct: return "direct";
        }
        throw new RuntimeException("Invalid visibility");
    }

    public static String createFilterContext(FilterContext context) {
        switch (context) {
            case Home: return "home";
            case Notifications: return "notifications";
            case Public: return "public";
            case Thread: return "thread";
            case Account: return "account";
        }
        throw new RuntimeException("Invalid filter context");
    }

    public static String createFilterAction(FilterAction action) {
        switch (action) {
            case Warn: return "warn";
            case Hide: return "hide";
        }
        throw new RuntimeException("Invalid filter action");
    }

    public static AttachmentKind createAttachmentKind(String kindStr) {
        if ("image".equals(kindStr)) return AttachmentKind.Image;
        else if ("video".equals(kindStr)) return AttachmentKind.Video;
        else throw new RuntimeException("Invalid attachment type");
    }

    public static List<GetStatus> createGetStatuses(StatusQueryResults statusQueryResults) {
        List<GetStatus> getStatuses = new ArrayList<>();
        for (StatusResultWithId result : statusQueryResults.results) getStatuses.add(new GetStatus(result, statusQueryResults.mentions));
        return getStatuses;
    }

    public static List<GetStatus> createGetStatuses(List<StatusQueryResult> statusQueryResults) {
        List<GetStatus> getStatuses = new ArrayList<>();
        for (StatusQueryResult statusQueryResult : statusQueryResults) getStatuses.add(new GetStatus(statusQueryResult.result, statusQueryResult.mentions));
        return getStatuses;
    }

    public static List<GetStatusEdit> createGetStatusEdits(AccountWithId accountWithId, List<Status> statuses) {
        List<GetStatusEdit> getStatusEdits = new ArrayList<>();
        for (Status status : statuses) getStatusEdits.add(new GetStatusEdit(accountWithId, status));
        return getStatusEdits;
    }

    public static List<GetAccount> createGetAccounts(List<AccountWithId> results) {
        List<GetAccount> getAccounts = new ArrayList<>();
        for (AccountWithId result : results) getAccounts.add(new GetAccount(result));
        return getAccounts;
    }

    public static List<GetList> createGetLists(List<AccountListWithId> lists) {
        List<GetList> getLists = new ArrayList<>();
        for (AccountListWithId list : lists) getLists.add(new GetList(list));
        return getLists;
    }

    public static List<GetConversation> createGetConversations(List<Conversation> convos) {
        List<GetConversation> getConversations = new ArrayList<>();
        for (Conversation convo : convos) getConversations.add(new GetConversation(convo));
        return getConversations;
    }

    public static List<GetFeatureHashtag> createGetFeatureHashtags(List<FeaturedHashtagInfo> infos) {
        List<GetFeatureHashtag> getFeatureHashtags = new ArrayList<>();
        for (FeaturedHashtagInfo info : infos) getFeatureHashtags.add(new GetFeatureHashtag(info.hashtag, info.numStatuses, info.timestamp));
        return getFeatureHashtags;
    }

    public static GetTag createGetTag(String hashtag, ItemStats stats, boolean isFollowing) {
        GetTag tag = new GetTag(hashtag);
        Map<Long, DayBucket> buckets = stats.dayBuckets;
        buckets.forEach((Long day, DayBucket b) -> {
            tag.history.add(new GetTag.HistoryItem(day, b.uses, b.accounts));
        });
        tag.following = isFollowing;
        return tag;
    }

    public static List<GetTag> createGetTags(Map<String, ItemStats> hashtagToStats) {
        List<GetTag> getTags = new ArrayList<>();
        hashtagToStats.forEach((String hashtag, ItemStats stats) -> {
            getTags.add(createGetTag(hashtag, stats, false));
        });
        return getTags;
    }

    public static List<GetTag> createGetTags(List<SimpleEntry<String, ItemStats>> hashtagToStats) {
        List<GetTag> getTags = new ArrayList<>();
        for (SimpleEntry<String, ItemStats> entry : hashtagToStats) {
            getTags.add(createGetTag(entry.getKey(), entry.getValue(), false));
        }
        return getTags;
    }

    public static GetPreviewCard createGetPreviewCard(String url, ItemStats stats) {
        GetPreviewCard link = new GetPreviewCard(url, "link");
        Map<Long, DayBucket> buckets = stats.dayBuckets;
        buckets.forEach((Long day, DayBucket b) -> {
            link.history.add(new GetPreviewCard.HistoryItem(day, b.uses, b.accounts));
        });
        return link;
    }

    public static List<GetPreviewCard> createGetPreviewCards(Map<String, ItemStats> linkToStats) {
        List<GetPreviewCard> getPreviewCards = new ArrayList<>();
        linkToStats.forEach((String url, ItemStats stats) -> {
            getPreviewCards.add(createGetPreviewCard(url, stats));
        });
        return getPreviewCards;
    }

    public static List<GetNotification> createGetNotifications(List<GetNotification.Bundle> bundles) {
        List<GetNotification> getNotifications = new ArrayList<>();
        for (GetNotification.Bundle bundle : bundles) {
            if (bundle != null) getNotifications.add(new GetNotification(bundle));
        }
        return getNotifications;
    }

    public static Map<String, GetMarker> createGetMarkers(Map<String, Marker> markers) {
        Map<String, GetMarker> getMarkers = new HashMap<>();
        for (Map.Entry<String, Marker> entry : markers.entrySet()) getMarkers.put(entry.getKey(), new GetMarker(entry.getValue()));
        return getMarkers;
    }

    public static GetReport createGetReport(PostReport params, AccountWithId targetAccount) {
        GetReport report = new GetReport();
        report.id = UUID.randomUUID().toString();
        report.category = params.category;
        report.comment = params.comment;
        report.created_at = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        report.status_ids = params.status_ids;
        if (params.rule_ids != null) report.rule_ids = params.rule_ids.stream().map(Object::toString).collect(Collectors.toList());
        report.target_account = new GetAccount(targetAccount);
        return report;
    }

    public static String parseStatusContent(String originalContent) {
        // for each link, associate the linked text with the host from the link.
        // this will be used to turn local mentions into fully-qualified remote mentions.
        Document doc = Jsoup.parse(originalContent);
        Map<String, String> textToHost = new HashMap<>();
        for (Element elem : doc.select("a")) {
            String text = elem.text();
            if (text.startsWith("@")) text = text.substring(1); // remove @ sign from mentions so the matching below works
            if (elem.hasAttr("href")) {
                try {
                    String href = elem.attr("href");
                    URL url = new URL(href);
                    String host = url.getHost();
                    // the host for this text is ambiguous, so we won't use it
                    if (textToHost.containsKey(text) && !host.equals(textToHost.get(text))) textToHost.put(text, null);
                    else textToHost.put(text, host);
                } catch (MalformedURLException e) {}
            }
        }

        // build our own status content without any html
        String content = "";
        for (Token token : Token.parseTokens(doc.text())) {
            switch (token.kind) {
                case BOUNDARY:
                case WORD:
                case LINK:
                case HASHTAG:
                    content += token.content;
                    break;
                case MENTION:
                    // add host to local mentions
                    String host = textToHost.get(token.content);
                    if (host != null && !host.isEmpty()) {
                        if (host.equals(MastodonConfig.API_DOMAIN)) content += token.content;
                        else content += String.format("%s@%s", token.content, host);
                    } else content += String.format("%s@unknown", token.content);
                    break;
                case REMOTE_MENTION:
                    // truncate remote mentions pointing to this server
                    String[] parts = token.content.split("@");
                    if (parts.length == 2 && parts[1].equals(MastodonConfig.API_DOMAIN)) content += parts[0];
                    else content += token.content;
                    break;
            }
        }
        return content;
    }

    public static String cleanHtmlTags(String originalContent) {
        Document doc = Jsoup.parse(originalContent);
        return doc.text();
    }

    public static PostStatus.Poll createPostStatusPoll(HashMap object) {
        PostStatus.Poll poll = new PostStatus.Poll();
        long endTime = Instant.parse((String) object.get("endTime")).toEpochMilli();
        poll.expires_in = (endTime - System.currentTimeMillis()) / 1000;
        List<HashMap> options;
        if (object.containsKey("oneOf")) {
            poll.multiple = false;
            options = (List<HashMap>) object.get("oneOf");
        } else if (object.containsKey("anyOf")) {
            poll.multiple = true;
            options = (List<HashMap>) object.get("anyOf");
        } else throw new RuntimeException("Poll requires oneOf or anyOf");
        poll.options = options.stream().map(option -> (String) option.get("name")).collect(Collectors.toList());
        return poll;
    }

    public static PostStatus createPostStatus(GetActivityPubProfile profile, HashMap object) {
        PostStatus postStatus = new PostStatus();
        postStatus.status = parseStatusContent((String) object.getOrDefault("content", ""));

        Set<String> to = new HashSet<>();
        Object toObj = object.get("to");
        if (toObj instanceof List) to.addAll((List<String>) toObj);
        else if (toObj instanceof String) to.add((String) toObj);
        else throw new RuntimeException("Invalid 'to' field");

        Set<String> cc = new HashSet<>();
        Object ccObj = object.get("cc");
        if (ccObj instanceof List) cc.addAll((List<String>) ccObj);
        else if (ccObj instanceof String) cc.add((String) ccObj);
        else throw new RuntimeException("Invalid 'cc' field");

        if (to.contains(MastodonWebHelpers.PUBLIC_URL)) postStatus.visibility = "public";
        else if (to.contains(profile.followers)) {
            if (cc.contains(MastodonWebHelpers.PUBLIC_URL)) postStatus.visibility = "unlisted";
            else postStatus.visibility = "private";
        } else postStatus.visibility = "direct";

        if ("Question".equals(object.get("type"))) postStatus.poll = createPostStatusPoll(object);
        return postStatus;
    }

    public static PostStatus createPostStatus(GetActivityPubProfile profile, HashMap object, StatusQueryResult parentStatus) {
        PostStatus postStatus = createPostStatus(profile, object);
        postStatus.in_reply_to_id = MastodonHelpers.serializeStatusPointer(new StatusPointer(parentStatus.result.status.author.accountId, parentStatus.result.statusId));
        return postStatus;
    }

    public static PutStatus createPutStatus(HashMap object) {
        PutStatus putStatus = new PutStatus();
        putStatus.status = parseStatusContent((String) object.getOrDefault("content", ""));
        if ("Question".equals(object.get("type"))) putStatus.poll = createPostStatusPoll(object);
        return putStatus;
    }

    public static PollContent getPollContent(StatusQueryResult statusQueryResult) {
        PollContent pollContent = null;
        if (statusQueryResult.result.status.content.isSetNormal()) {
            pollContent = statusQueryResult.result.status.content.getNormal().pollContent;
        } else if (statusQueryResult.result.status.content.isSetReply()) {
            pollContent = statusQueryResult.result.status.content.getReply().pollContent;
        }
        return pollContent;
    }

    public static Map<String, String> createSearchParams(Long nextId, String term) {
        if (nextId != null && term != null) {
            return new HashMap(){{
                put("nextId", nextId);
                put("term", term);
            }};
        } else {
            return null;
        }
    }

    public static Map<String, String> createSearchParams(Map searchParams) {
        Long nextId = (Long) searchParams.get("nextId");
        String term = (String) searchParams.get("term");
        return createSearchParams(nextId, term);
    }

    public static List<SimpleEntry<String, String>> createLinkHeaderParams(Map searchParams) {
        if (searchParams != null) {
            List<SimpleEntry<String, String>> params = new ArrayList<>();
            params.add(new SimpleEntry<>("start_next_id", searchParams.get("nextId") + ""));
            params.add(new SimpleEntry<>("start_term", searchParams.get("term") + ""));
            return params;
        } else {
            return null;
        }
    }

    public static String getUsernameFromProfileUrl(URL url) {
        String[] parts = url.getPath().split("/");
        if (parts.length == 3 && parts[1].equals("users")) return parts[2];
        else if (parts.length == 2 && parts[1].startsWith("@")) return parts[1].substring(1);
        throw new RuntimeException("Couldn't find username in " + url.getPath());
    }

    public static GetActivityPubProfile createProfile(AccountWithId accountWithId) {
        String name = accountWithId.account.name;
        GetActivityPubProfile profile = new GetActivityPubProfile();
        profile.id = String.format("%s/users/%s", MastodonConfig.API_URL, name);
        profile.type = "Person";
        profile.following = String.format("%s/users/%s/following", MastodonConfig.API_URL, name);
        profile.followers = String.format("%s/users/%s/followers", MastodonConfig.API_URL, name);
        profile.inbox = String.format("%s/users/%s/inbox", MastodonConfig.API_URL, name);
        profile.outbox = String.format("%s/users/%s/outbox", MastodonConfig.API_URL, name);
        profile.featured = String.format("%s/users/%s/collections/featured", MastodonConfig.API_URL, name);
        profile.featuredTags = String.format("%s/users/%s/collections/tags", MastodonConfig.API_URL, name);
        profile.preferredUsername = name;
        profile.name = accountWithId.account.isSetDisplayName() ? accountWithId.account.displayName : "";
        profile.summary = accountWithId.account.isSetBio() ? accountWithId.account.bio : "";
        profile.url = String.format("%s/@%s", MastodonConfig.API_URL, name);
        profile.manuallyApprovesFollowers = accountWithId.account.locked;
        profile.discoverable = accountWithId.account.isSetDiscoverable() ? accountWithId.account.discoverable : false;
        profile.published = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        profile.devices = String.format("%s/users/%s/collections/devices", MastodonConfig.API_URL, name);
        profile.attachment = new ArrayList<>();
        if (accountWithId.account.content.isSetLocal()) {
            profile.publicKey = new GetActivityPubProfile.PublicKey(MastodonWebHelpers.getKeyId(accountWithId.account), profile.id, accountWithId.account.publicKey);
        }
        else throw new RuntimeException("User is not local: " + name);
        if (accountWithId.account.isSetFields()) {
            for (KeyValuePair pair : accountWithId.account.fields) {
                profile.attachment.add(new GetActivityPubProfile.Attachment("PropertyValue", pair.key, pair.value));
            }
        }
        GetAccount getAccount = new GetAccount(accountWithId.accountId, accountWithId.account, null);
        profile.icon = new GetActivityPubProfile.Media();
        profile.icon.type = "Image";
        profile.icon.mediaType = "image/" + FilenameUtils.getExtension(getAccount.avatar_static).toLowerCase();
        profile.icon.url = getAccount.avatar_static;
        profile.image = new GetActivityPubProfile.Media();
        profile.image.type = "Image";
        profile.image.mediaType = "image/" + FilenameUtils.getExtension(getAccount.header_static).toLowerCase();
        profile.image.url = getAccount.header_static;
        profile.endpoints = new HashMap(){{ put("sharedInbox", String.format("%s/inbox", MastodonConfig.API_URL)); }};
        return profile;
    }

    public static List<EditAccountField> getEditsFromProfile(AccountWithId accountWithId, GetActivityPubProfile profile) {
       // get new avatar/header
       GetActivityPubProfile.Media avatar = profile.icon;
       GetActivityPubProfile.Media header = profile.image;
       String newAvatarPath = null;
       if (avatar != null && "Image".equals(avatar.type)) newAvatarPath = avatar.url;
       String newHeaderPath = null;
       if (header != null && "Image".equals(avatar.type)) newHeaderPath = header.url;

       // get old avatar/header
       String existingAvatarPath = null;
       if (accountWithId.account.isSetAvatar()) existingAvatarPath = accountWithId.account.avatar.attachment.path;
       String existingHeaderPath = null;
       if (accountWithId.account.isSetHeader()) existingHeaderPath = accountWithId.account.header.attachment.path;

       // accumulate the edits
       List<EditAccountField> edits = new ArrayList<>();

       if (!StringUtils.equals(newAvatarPath, existingAvatarPath)) {
           if (newAvatarPath == null) edits.add(EditAccountField.avatar(new AttachmentWithId("", new Attachment(AttachmentKind.Image, "", ""))));
           else edits.add(EditAccountField.avatar(new AttachmentWithId("", new Attachment(AttachmentKind.Image, newAvatarPath, ""))));
       }
       if (!StringUtils.equals(newHeaderPath, existingHeaderPath)) {
           if (newHeaderPath == null) edits.add(EditAccountField.header(new AttachmentWithId("", new Attachment(AttachmentKind.Image, "", ""))));
           else edits.add(EditAccountField.header(new AttachmentWithId("", new Attachment(AttachmentKind.Image, newHeaderPath, ""))));
       }

       String displayName = profile.name;
       String bio = profile.summary;
       GetActivityPubProfile.PublicKey publicKey = profile.publicKey;
       boolean discoverable = profile.discoverable;
       boolean locked = profile.manuallyApprovesFollowers;

       if (displayName != null) edits.add(EditAccountField.displayName(displayName));
       if (bio != null) edits.add(EditAccountField.bio(MastodonApiHelpers.cleanHtmlTags(bio)));
       if (publicKey != null) edits.add(EditAccountField.publicKey(publicKey.publicKeyPem));
       edits.add(EditAccountField.discoverable(discoverable));
       edits.add(EditAccountField.locked(locked));

       return edits;
    }
}
