package org.hyperledger.besu.enclave.types;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

// TODO: fix
@JsonPropertyOrder({"payload", "from", "to", "otVar"})
public class SendRequestPrivacy extends SendRequestLegacy{
  private final String otVar;

  public SendRequestPrivacy(
      @JsonProperty(value = "payload") final String payload,
      @JsonProperty(value = "from") final String from,
      @JsonProperty(value = "to") final List<String> to,
      @JsonProperty(value = "otVar") final String otVar) {
    super(payload, from, to);
    this.otVar = otVar;
  }

  public String getOtVar(){
      return otVar;
  }

}
