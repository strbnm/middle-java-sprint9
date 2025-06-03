import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.strbnm.front_ui.client.exchange.api.ExchangeServiceApi;
import ru.strbnm.front_ui.client.exchange.domain.Rate;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ExchangeServiceApi exchangeServiceApi;

    @GetMapping("/")
    public Mono<String> redirectToMain() {
        return Mono.just("redirect:/main");
    }

    @GetMapping("/main")
    public Mono<String> mainPage(Model model, WebSession session) {
        String login = userService.getCurrentLogin();

        return Mono.zip(
                userService.getUserFullName(login),
                userService.getUserBirthdate(login),
                userService.getUserAccounts(login),
                userService.getAllCurrencies(),
                userService.getAllUsers()
        ).doOnNext(tuple -> {
            model.addAttribute("login", login);
            model.addAttribute("name", tuple.getT1());
            model.addAttribute("birthdate", tuple.getT2());
            model.addAttribute("accounts", tuple.getT3());
            model.addAttribute("currency", tuple.getT4());
            model.addAttribute("users", tuple.getT5());

            addSessionErrorsToModel(session, model, "passwordErrors");
            addSessionErrorsToModel(session, model, "userAccountsErrors");
            addSessionErrorsToModel(session, model, "cashErrors");
            addSessionErrorsToModel(session, model, "transferErrors");
            addSessionErrorsToModel(session, model, "transferOtherErrors");
        }).thenReturn("main");
    }

    @PostMapping("/user/{login}/editPassword")
    public Mono<String> editPassword(@PathVariable String login,
                                     @RequestParam String password,
                                     @RequestParam("confirm_password") String confirmPassword,
                                     WebSession session) {
        return userService.updatePassword(login, password, confirmPassword)
                .doOnNext(errors -> {
                    if (!errors.isEmpty()) {
                        session.getAttributes().put("passwordErrors", errors);
                    }
                })
                .thenReturn("redirect:/main");
    }

    @PostMapping("/user/{login}/editUserAccounts")
    public Mono<String> editUserAccounts(@PathVariable String login,
                                         @RequestParam String name,
                                         @RequestParam LocalDate birthdate,
                                         @RequestParam(required = false, name = "account") List<String> accounts,
                                         WebSession session) {
        return userService.updateUserAccounts(login, name, birthdate, accounts)
                .doOnNext(errors -> {
                    if (!errors.isEmpty()) {
                        session.getAttributes().put("userAccountsErrors", errors);
                    }
                })
                .thenReturn("redirect:/main");
    }

    @PostMapping("/user/{login}/cash")
    public Mono<String> cashOperation(@PathVariable String login,
                                      @RequestParam String currency,
                                      @RequestParam double value,
                                      @RequestParam Action action,
                                      WebSession session) {
        return userService.processCash(login, currency, value, action)
                .doOnNext(errors -> {
                    if (!errors.isEmpty()) {
                        session.getAttributes().put("cashErrors", errors);
                    }
                })
                .thenReturn("redirect:/main");
    }

    @PostMapping("/user/{login}/transfer")
    public Mono<String> transfer(@PathVariable String login,
                                 @RequestParam("from_currency") String fromCurrency,
                                 @RequestParam("to_currency") String toCurrency,
                                 @RequestParam double value,
                                 @RequestParam("to_login") String toLogin,
                                 WebSession session) {
        return userService.transfer(login, fromCurrency, toCurrency, value, toLogin)
                .doOnNext(errors -> {
                    if (!errors.isEmpty()) {
                        String key = login.equals(toLogin) ? "transferErrors" : "transferOtherErrors";
                        session.getAttributes().put(key, errors);
                    }
                })
                .thenReturn("redirect:/main");
    }

    @GetMapping("/signup")
    public Mono<String> signupForm() {
        return Mono.just("signup");
    }

    @PostMapping("/signup")
    public Mono<String> signup(@RequestParam String login,
                               @RequestParam String password,
                               @RequestParam("confirm_password") String confirmPassword,
                               @RequestParam String name,
                               @RequestParam LocalDate birthdate,
                               Model model) {
        return userService.signup(login, password, confirmPassword, name, birthdate)
                .flatMap(errors -> {
                    if (!errors.isEmpty()) {
                        model.addAttribute("login", login);
                        model.addAttribute("name", name);
                        model.addAttribute("birthdate", birthdate);
                        model.addAttribute("errors", errors);
                        return Mono.just("signup");
                    }
                    return Mono.just("redirect:/main");
                });
    }

    @GetMapping("/api/rates")
    @ResponseBody
    public Flux<Rate> getRates() {
        return exchangeServiceApi.getRates();
    }

    private void addSessionErrorsToModel(WebSession session, Model model, String attributeName) {
        if (session.getAttributes().containsKey(attributeName)) {
            model.addAttribute(attributeName, session.getAttribute(attributeName));
            session.getAttributes().remove(attributeName);
        } else {
            model.addAttribute(attributeName, null);
        }
    }

    public enum Action {
        PUT, GET
    }
}
