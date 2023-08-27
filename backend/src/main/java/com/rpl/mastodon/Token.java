package com.rpl.mastodon;

import java.util.*;
import com.rpl.rama.*;

public class Token implements RamaSerializable {
    private static final HashSet<Character> linkBoundaryChars =
      new HashSet<>(Arrays.asList(
        // whitespace chars
        ' ', '\t', '\n', '\r', '\f', '\b',
        // special chars not allowed in URLs
        '`', '\'', '"', '(', ')', '[', ']', '{', '}', '<', '>'
      ));
    private static final HashSet<Character> boundaryChars =
            new HashSet<>(Arrays.asList(
                    // whitespace chars
                    ' ', '\t', '\n', '\r', '\f', '\b',
                    // special chars
                    '!', '$', '%', '^', '&', '*', '?', '\\', '.', ',', '`', '\'', '"', ';',
                    '|', '-', '+', '=', '(', ')', '[', ']', '{', '}', '<', '>'
            ));

    public enum TokenKind {
        BOUNDARY,
        WORD,
        LINK,
        HASHTAG,
        MENTION,
        REMOTE_MENTION
    }

    public TokenKind kind;
    public String content;

    private static boolean isLink(String content) {
        return content.startsWith("http://") || content.startsWith("https://");
    }

    private static void finishToken(List<Token> tokens, Token token) {
        if (token.content.length() > 0) tokens.add(token);
    }

    public static List<Token> parseTokens(String content) {
        List<Token> tokens = new ArrayList<>();
        Token currentToken = new Token(TokenKind.BOUNDARY, "");
        for (char ch : content.toCharArray()) {
            boolean linkParsing = currentToken.kind == TokenKind.LINK || currentToken.kind == TokenKind.REMOTE_MENTION;
            Set<Character> chars = linkParsing ? linkBoundaryChars : boundaryChars;
            if (chars.contains(ch)) {
                if (currentToken.kind == TokenKind.BOUNDARY) currentToken.content += ch;
                else {
                    finishToken(tokens, currentToken);
                    currentToken = new Token(TokenKind.BOUNDARY, String.valueOf(ch));
                }
            } else if (!linkParsing && ch == '#') {
                finishToken(tokens, currentToken);
                currentToken = new Token(TokenKind.BOUNDARY, "#");
                finishToken(tokens, currentToken);
                currentToken = new Token(TokenKind.HASHTAG, "");
            } else if (!linkParsing && ch == '@') {
                if (currentToken.kind == TokenKind.MENTION) {
                    currentToken.content += ch;
                    currentToken.kind = TokenKind.REMOTE_MENTION;
                }  else {
                    finishToken(tokens, currentToken);
                    currentToken = new Token(TokenKind.BOUNDARY, "@");
                    finishToken(tokens, currentToken);
                    currentToken = new Token(TokenKind.MENTION, "");
                }
            }
            else {
                if (currentToken.kind == TokenKind.BOUNDARY) {
                    finishToken(tokens, currentToken);
                    currentToken = new Token(TokenKind.WORD, String.valueOf(ch));
                } else currentToken.content += ch;
                if (currentToken.kind == TokenKind.WORD && isLink(currentToken.content)) currentToken.kind = TokenKind.LINK;
            }
        }
        finishToken(tokens, currentToken);
        return tokens;
    }

    public static Set<String> filterHashtags(List<Token> tokens) {
        Set<String> hashtags = new HashSet<>();
        for(Token token : tokens) {
            if(token.kind == TokenKind.HASHTAG) hashtags.add(token.content);
        }
        return hashtags;
    }

    public static Set<String> filterMentions(List<Token> tokens) {
        HashSet<String> mentions = new HashSet<>();
        for(Token token : tokens) {
            if(token.kind == TokenKind.MENTION || token.kind == TokenKind.REMOTE_MENTION) mentions.add(token.content);
        }
        return mentions;
    }

    public static Set<String> filterLinks(List<Token> tokens) {
        HashSet<String> urls = new HashSet<>();
        for(Token token : tokens) {
            if(token.kind == TokenKind.LINK) urls.add(token.content);
        }
        return urls;
    }

    public Token(TokenKind kind, String content) {
        this.kind = kind;
        this.content = content;
    }

    public String getOrigContent() {
        if(kind==TokenKind.HASHTAG) return "#" + content;
        else if(kind==TokenKind.MENTION) return "@" + content;
        else return content;
    }

    @Override
    public String toString() {
        return "Token{kind=" + kind + ", content='" + content + '\'' + '}';
    }
}
