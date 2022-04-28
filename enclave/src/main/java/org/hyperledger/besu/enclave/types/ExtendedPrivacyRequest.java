package org.hyperledger.besu.enclave.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"protocolId", "privateArgs", "pmt", "recipients"})
public class ExtendedPrivacyRequest {
    private final byte[] protocolId;
    private final byte[] privateArgs;
    private final byte[] pmt;
    private final String[] recipients;

    @JsonCreator
    public ExtendedPrivacyRequest(
      @JsonProperty(value = "protocolId") final byte[] protocolId,
      @JsonProperty(value = "privateArgs") final byte[] privateArgs,
      @JsonProperty(value = "pmt") final byte[] pmt,
      @JsonProperty(value = "recipients") final String[] recipients) {
        this.protocolId = protocolId;
        this.privateArgs = privateArgs;
        this.pmt = pmt;
        this.recipients = recipients;
    }

    public byte[] getProtocolId() {
        return protocolId;
    }

    public byte[] getPrivateArgs() {
        return privateArgs;
    }

    public byte[] getPmt() {
        return pmt;
    }

    public String[] getRecipients() {
        return recipients;
    }
    
}
