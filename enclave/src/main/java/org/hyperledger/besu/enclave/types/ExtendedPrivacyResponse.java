package org.hyperledger.besu.enclave.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"result"})
public class ExtendedPrivacyResponse {
    private final byte[] result; 

    @JsonCreator
    public ExtendedPrivacyResponse(@JsonProperty("result") final byte[] result) {
        this.result = result;
    }

    public byte[] getResult() {
        return result;
    }
}
