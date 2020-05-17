package net.pwall.xml;

import java.util.Objects;

public class PrefixMapping {

    private final String prefix;
    private final String uri;

    public PrefixMapping(String prefix, String uri) {
        this.prefix = Objects.requireNonNull(prefix);
        this.uri = Objects.requireNonNull(uri);
    }

    public String getPrefix() {
        return prefix;
    }

    public String getUri() {
        return uri;
    }

    public String getXmlns() {
        return prefix.length() == 0 ? "xmlns" : "xmlns:" + prefix;
    }

}
