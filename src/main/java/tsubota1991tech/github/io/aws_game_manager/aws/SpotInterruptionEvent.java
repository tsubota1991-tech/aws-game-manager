package tsubota1991tech.github.io.aws_game_manager.aws;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SpotInterruptionEvent {

    private String id;
    private String source;

    @JsonProperty("detail-type")
    private String detailType;

    private String account;
    private Instant time;
    private Detail detail;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDetailType() {
        return detailType;
    }

    public void setDetailType(String detailType) {
        this.detailType = detailType;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public Instant getTime() {
        return time;
    }

    public void setTime(Instant time) {
        this.time = time;
    }

    public Detail getDetail() {
        return detail;
    }

    public void setDetail(Detail detail) {
        this.detail = detail;
    }

    public static class Detail {
        @JsonProperty("instance-id")
        private String instanceId;

        @JsonProperty("instance-action")
        private String instanceAction;

        public String getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(String instanceId) {
            this.instanceId = instanceId;
        }

        public String getInstanceAction() {
            return instanceAction;
        }

        public void setInstanceAction(String instanceAction) {
            this.instanceAction = instanceAction;
        }
    }
}
