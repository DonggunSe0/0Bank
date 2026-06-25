package com.team10.backend.domain.investment.realtime.service;

import java.util.UUID;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Getter
@Component
public class RealtimeOrderbookInstanceIdProvider {

    private final String instanceId = UUID.randomUUID().toString();

}
