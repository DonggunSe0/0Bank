package com.team10.backend.domain.exAccount.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/external-accounts")
@Tag(name = "External Account", description = "외부 계좌 API")
public class ExAccountController {
}
