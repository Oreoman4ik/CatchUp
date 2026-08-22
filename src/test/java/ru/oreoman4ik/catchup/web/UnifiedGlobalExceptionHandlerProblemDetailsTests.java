package ru.oreoman4ik.catchup.web;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.oreoman4ik.catchup.autoconfigure.UnifiedErrorAutoConfiguration;

import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

class UnifiedGlobalExceptionHandlerProblemDetailsTests {

    @Nested
    @WebMvcTest(
            controllers = TestController.class,
            properties = {
                    "spring.mvc.problemdetails.enabled=true",
                    "spring.application.name=test-service"
            }
    )
    @ContextConfiguration(
            classes = TestController.class
    )
    @ImportAutoConfiguration(
            UnifiedErrorAutoConfiguration.class
    )
    class ProblemDetailsEnabled {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void libraryFormatWins()
                throws Exception {

            assertLibraryFormat(
                    mockMvc
            );
        }
    }

    @Nested
    @WebMvcTest(
            controllers = TestController.class,
            properties = {
                    "spring.mvc.problemdetails.enabled=false",
                    "spring.application.name=test-service"
            }
    )
    @ContextConfiguration(
            classes = TestController.class
    )
    @ImportAutoConfiguration(
            UnifiedErrorAutoConfiguration.class
    )
    class ProblemDetailsDisabled {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void libraryFormatIsStillUsed()
                throws Exception {

            assertLibraryFormat(
                    mockMvc
            );
        }
    }

    private static void assertLibraryFormat(
            MockMvc mockMvc
    ) throws Exception {

        mockMvc.perform(
                        post(
                                "/problem-details-test"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        "{broken-json"
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value(
                                        "INVALID_REQUEST_BODY"
                                )
                )
                .andExpect(
                        jsonPath("$.errorId")
                                .exists()
                )
                .andExpect(
                        jsonPath("$.chain")
                                .isArray()
                )
                .andExpect(
                        jsonPath("$.title")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.detail")
                                .doesNotExist()
                );
    }

    @RestController
    static class TestController {

        @PostMapping(
                "/problem-details-test"
        )
        String test(
                @RequestBody Request request
        ) {
            return "ok";
        }
    }

    record Request(
            Integer value
    ) {
    }
}