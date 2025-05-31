package ru.strbnm.blocker_service.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.strbnm.blocker_service.api.BlockerServiceApi;
import ru.strbnm.blocker_service.domain.CheckCashTransactionRequest;
import ru.strbnm.blocker_service.domain.CheckTransactionResponse;
import ru.strbnm.blocker_service.domain.CheckTransferTransactionRequest;
import ru.strbnm.blocker_service.service.CheckTransactionService;

@Controller
@RequestMapping("${openapi.blockerService.base-path:/}")
public class CheckTransactionController implements BlockerServiceApi {
  private final CheckTransactionService checkTransactionService;

  @Autowired
  public CheckTransactionController(CheckTransactionService checkTransactionService) {
    this.checkTransactionService = checkTransactionService;
  }

  @Override
  public Mono<ResponseEntity<CheckTransactionResponse>> checkCashTransaction(
      Mono<CheckCashTransactionRequest> checkCashTransactionRequest, ServerWebExchange exchange) {
    return checkCashTransactionRequest.flatMap(
        request ->
            checkTransactionService
                .checkCashTransaction(request)
                .map(
                    checkResult -> {
                      CheckTransactionResponse response = new CheckTransactionResponse();
                      response.setTransactionId(request.getTransactionId());
                      response.setIsBlocked(checkResult.isBlocked());
                      response.setReason(checkResult.reason());
                      return ResponseEntity.ok(response);
                    }));
  }

  @Override
  public Mono<ResponseEntity<CheckTransactionResponse>> checkTransferTransaction(
      Mono<CheckTransferTransactionRequest> checkTransferTransactionRequest,
      ServerWebExchange exchange) {
    return checkTransferTransactionRequest.flatMap(
        request ->
            checkTransactionService
                .checkTransferTransaction(request)
                .map(
                    checkResult -> {
                      CheckTransactionResponse response = new CheckTransactionResponse();
                      response.setTransactionId(request.getTransactionId());
                      response.setIsBlocked(checkResult.isBlocked());
                      response.setReason(checkResult.reason());
                      return ResponseEntity.ok(response);
                    }));
  }
}
