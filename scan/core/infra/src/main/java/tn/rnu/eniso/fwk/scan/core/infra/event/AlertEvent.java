package tn.rnu.eniso.fwk.scan.core.infra.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import tn.rnu.eniso.fwk.scan.core.infra.model.Alert;

@Getter
public class AlertEvent extends ApplicationEvent {
    private final Alert alert;

    public AlertEvent(Object source, Alert alert) {
        super(source);
        this.alert = alert;
    }
}
