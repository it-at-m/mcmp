package de.muenchen.mcmp.infoblox.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "_ref",
        "canonical",
        "name"
})
public class JsonInfobloxSearch implements Serializable
{
    @JsonProperty("_ref")
    private String ref;
    @JsonProperty("canonical")
    private String canonical;
    @JsonProperty("name")
    private String name;
    private final static long serialVersionUID = -5248351496062640952L;

    public JsonInfobloxSearch() {
    }

    public JsonInfobloxSearch(final String ref, final String canonical, final String name) {
        super();
        this.ref = ref;
        this.canonical = canonical;
        this.name = name;
    }

    @JsonProperty("_ref")
    public String getRef() {
        return this.ref;
    }

    @JsonProperty("_ref")
    public void setRef(final String ref) {
        this.ref = ref;
    }

    public JsonInfobloxSearch withRef(final String ref) {
        this.ref = ref;
        return this;
    }

    @JsonProperty("canonical")
    public String getCanonical() {
        return this.canonical;
    }

    @JsonProperty("canonical")
    public void setCanonical(final String canonical) {
        this.canonical = canonical;
    }

    public JsonInfobloxSearch withCanonical(final String canonical) {
        this.canonical = canonical;
        return this;
    }

    @JsonProperty("name")
    public String getName() {
        return this.name;
    }

    @JsonProperty("name")
    public void setName(final String name) {
        this.name = name;
    }

    public JsonInfobloxSearch withName(final String name) {
        this.name = name;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(JsonInfobloxSearch.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("ref");
        sb.append('=');
        sb.append(((this.ref == null)?"<null>":this.ref));
        sb.append(',');
        sb.append("canonical");
        sb.append('=');
        sb.append(((this.canonical == null)?"<null>":this.canonical));
        sb.append(',');
        sb.append("name");
        sb.append('=');
        sb.append(((this.name == null)?"<null>":this.name));
        sb.append(',');
        if (sb.charAt((sb.length()- 1)) == ',') {
            sb.setCharAt((sb.length()- 1), ']');
        } else {
            sb.append(']');
        }
        return sb.toString();
    }
}