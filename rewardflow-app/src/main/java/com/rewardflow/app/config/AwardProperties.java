package com.rewardflow.app.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Award-related default settings for this module. */
@Validated
@ConfigurationProperties(prefix = "rewardflow.award")
public class AwardProperties {

  /** 此场景的默认奖品代码（可扩展为多奖）。 */
  @NotBlank
  private String prizeCode = "COIN";

  public String getPrizeCode() {
    return prizeCode;
  }

  public void setPrizeCode(String prizeCode) {
    this.prizeCode = prizeCode;
  }
}
