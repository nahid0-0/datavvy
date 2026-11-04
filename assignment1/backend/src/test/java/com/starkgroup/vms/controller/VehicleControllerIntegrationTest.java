package com.starkgroup.vms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starkgroup.vms.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VehicleControllerIntegrationTest {

    private static final String BASE = "/api/v1/vehicles";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VehicleRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    private static Map<String, Object> validBody(String number) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vehicleNumber", number);
        body.put("make", "Toyota");
        body.put("model", "Corolla");
        body.put("type", "CAR");
        body.put("manufactureYear", 2021);
        body.put("color", "White");
        body.put("ownerName", "Tony Stark");
        return body;
    }

    private ResultActions postJson(Object body) throws Exception {
        return mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : objectMapper.writeValueAsString(body)));
    }

    private ResultActions putJson(long id, Object body) throws Exception {
        return mvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode create(String number) throws Exception {
        String json = postJson(validBody(number)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    // ------------------------------------------------------------------ create

    @Nested
    class Create {

        @Test
        void createsVehicleAndReturnsLocation() throws Exception {
            postJson(validBody("  dha-metro  ga 12-3456 "))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", containsString(BASE + "/")))
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.vehicleNumber").value("DHA-METRO GA 12-3456"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.version").value(0))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty());
        }

        @Test
        void trimsStringsAndStoresBlankOptionalsAsNull() throws Exception {
            Map<String, Object> body = validBody("DHA-1");
            body.put("make", "  Toyota  ");
            body.put("color", "   ");
            postJson(body)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.make").value("Toyota"))
                    .andExpect(jsonPath("$.color").value(nullValue()));
        }

        @Test
        void acceptsCaseInsensitiveEnums() throws Exception {
            Map<String, Object> body = validBody("DHA-2");
            body.put("type", "truck");
            body.put("status", "maintenance");
            postJson(body)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type").value("TRUCK"))
                    .andExpect(jsonPath("$.status").value("MAINTENANCE"));
        }

        @Test
        void rejectsDuplicateNumberIgnoringCaseSpacesAndHyphens() throws Exception {
            create("DHA-1234");

            for (String variant : new String[]{"DHA-1234", "dha-1234", "DHA 1234", "dha1234", " D-HA 12 34 "}) {
                postJson(validBody(variant))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("DUPLICATE_VEHICLE_NUMBER"))
                        .andExpect(jsonPath("$.errors.vehicleNumber").exists());
            }
            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        void supportsBengaliPlatesIncludingCombiningMarksAndDetectsDuplicates() throws Exception {
            postJson(validBody("ঢাকা মেট্রো-গ ১২-৩৪৫৬"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.vehicleNumber").value("ঢাকা মেট্রো-গ ১২-৩৪৫৬"));

            postJson(validBody("ঢাকা-মেট্রো গ ১২৩৪৫৬"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_VEHICLE_NUMBER"));
        }

        @Test
        void rejectsMissingRequiredFieldsWithPerFieldErrors() throws Exception {
            postJson("{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors.vehicleNumber").exists())
                    .andExpect(jsonPath("$.errors.make").exists())
                    .andExpect(jsonPath("$.errors.model").exists())
                    .andExpect(jsonPath("$.errors.type").exists())
                    .andExpect(jsonPath("$.errors.manufactureYear").exists());
        }

        @Test
        void rejectsWhitespaceOnlyRequiredField() throws Exception {
            Map<String, Object> body = validBody("DHA-3");
            body.put("make", "    ");
            postJson(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.make").exists());
        }

        @Test
        void rejectsInvalidVehicleNumberFormats() throws Exception {
            for (String bad : new String[]{"-DHA1", "DHA1-", "DHA#123", "ABCDEF", "A", "1".repeat(33), "DHA_12"}) {
                postJson(validBody(bad))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errors.vehicleNumber").exists());
            }
        }

        @Test
        void rejectsOutOfRangeManufactureYear() throws Exception {
            int nextYear = Year.now().getValue() + 1;
            for (int year : new int[]{1885, nextYear + 1, -5}) {
                Map<String, Object> body = validBody("DHA-4");
                body.put("manufactureYear", year);
                postJson(body)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errors.manufactureYear").value(containsString(String.valueOf(nextYear))));
            }
            Map<String, Object> ok = validBody("DHA-4");
            ok.put("manufactureYear", nextYear);
            postJson(ok).andExpect(status().isCreated());
        }

        @Test
        void rejectsUnknownEnumValueWithHelpfulMessage() throws Exception {
            Map<String, Object> body = validBody("DHA-5");
            body.put("type", "SPACESHIP");
            postJson(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").value(containsString("CAR")));
        }

        @Test
        void rejectsWrongJsonTypes() throws Exception {
            Map<String, Object> body = validBody("DHA-6");
            body.put("manufactureYear", "two thousand");
            postJson(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.manufactureYear").value("must be a number"));
        }

        @Test
        void rejectsOverlongFields() throws Exception {
            Map<String, Object> body = validBody("DHA-7");
            body.put("make", "x".repeat(51));
            body.put("ownerName", "y".repeat(101));
            postJson(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.make").exists())
                    .andExpect(jsonPath("$.errors.ownerName").exists());
        }

        @Test
        void rejectsMalformedJson() throws Exception {
            postJson("{\"vehicleNumber\": ")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }

        @Test
        void rejectsNonJsonContentType() throws Exception {
            mvc.perform(post(BASE).contentType(MediaType.TEXT_PLAIN).content("hello"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        }

        @Test
        void ignoresClientSuppliedId() throws Exception {
            Map<String, Object> body = validBody("DHA-8");
            body.put("id", 999_999);
            JsonNode created = objectMapper.readTree(postJson(body).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString());
            assertThat(created.get("id").asLong()).isNotEqualTo(999_999L);
        }
    }

    // ------------------------------------------------------------------ read

    @Nested
    class Read {

        @Test
        void getById() throws Exception {
            long id = create("DHA-10").get("id").asLong();
            mvc.perform(get(BASE + "/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.vehicleNumber").value("DHA-10"));
        }

        @Test
        void getMissing_returns404() throws Exception {
            mvc.perform(get(BASE + "/123456"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }

        @Test
        void getWithNonNumericId_returns400() throws Exception {
            mvc.perform(get(BASE + "/abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.id").value("must be a number"));
        }

        @Test
        void getWithNegativeId_returns400() throws Exception {
            mvc.perform(get(BASE + "/-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        void listIsEmptyInitially() throws Exception {
            mvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void listSupportsSearchFiltersPagingAndSorting() throws Exception {
            create("DHA-100");
            create("CTG-200");
            Map<String, Object> truck = validBody("SYL-300");
            truck.put("type", "TRUCK");
            truck.put("make", "Tata");
            postJson(truck).andExpect(status().isCreated());

            // search by normalized plate (no hyphen) and by make, case-insensitively
            mvc.perform(get(BASE).param("search", "ctg200"))
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].vehicleNumber").value("CTG-200"));
            mvc.perform(get(BASE).param("search", "TATA"))
                    .andExpect(jsonPath("$.content", hasSize(1)));

            // filter
            mvc.perform(get(BASE).param("type", "TRUCK"))
                    .andExpect(jsonPath("$.totalElements").value(1));

            // paging + sorting
            mvc.perform(get(BASE).param("size", "2").param("page", "0").param("sort", "vehicleNumber,asc"))
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].vehicleNumber").value("CTG-200"))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.first").value(true))
                    .andExpect(jsonPath("$.last").value(false));

            // page beyond the end is an empty page, not an error
            mvc.perform(get(BASE).param("page", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        void searchTreatsLikeWildcardsLiterally() throws Exception {
            create("DHA-100");
            mvc.perform(get(BASE).param("search", "%"))
                    .andExpect(jsonPath("$.content", hasSize(0)));
            mvc.perform(get(BASE).param("search", "_"))
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        void pageSizeIsCapped() throws Exception {
            mvc.perform(get(BASE).param("size", "5000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(100));
        }

        @Test
        void unknownSortField_returns400() throws Exception {
            mvc.perform(get(BASE).param("sort", "password,asc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.sort").exists());
        }

        @Test
        void invalidFilterValue_returns400() throws Exception {
            mvc.perform(get(BASE).param("type", "BOAT"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").exists());
        }
    }

    // ------------------------------------------------------------------ update

    @Nested
    class Update {

        @Test
        void updatesFieldsAndBumpsVersion() throws Exception {
            JsonNode created = create("DHA-20");
            long id = created.get("id").asLong();

            Map<String, Object> body = validBody("DHA-21");
            body.put("model", "Camry");
            body.put("status", "INACTIVE");
            body.put("version", created.get("version").asLong());

            putJson(id, body)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.vehicleNumber").value("DHA-21"))
                    .andExpect(jsonPath("$.model").value("Camry"))
                    .andExpect(jsonPath("$.status").value("INACTIVE"))
                    .andExpect(jsonPath("$.version").value(1));
        }

        @Test
        void keepingOwnNumberIsNotADuplicate() throws Exception {
            long id = create("DHA-22").get("id").asLong();
            putJson(id, validBody("dha 22")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.vehicleNumber").value("DHA 22"));
        }

        @Test
        void omittedStatusIsLeftUnchanged() throws Exception {
            Map<String, Object> body = validBody("DHA-23");
            body.put("status", "MAINTENANCE");
            long id = objectMapper.readTree(postJson(body).andReturn().getResponse().getContentAsString())
                    .get("id").asLong();

            putJson(id, validBody("DHA-23"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("MAINTENANCE"));
        }

        @Test
        void takingAnotherVehiclesNumberIsRejected() throws Exception {
            create("DHA-24");
            long id = create("DHA-25").get("id").asLong();

            putJson(id, validBody("DHA 24"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_VEHICLE_NUMBER"));
        }

        @Test
        void staleVersionIsRejected() throws Exception {
            long id = create("DHA-26").get("id").asLong();
            Map<String, Object> first = validBody("DHA-26");
            first.put("color", "Red"); // a real change, so the version is bumped to 1
            first.put("version", 0);
            putJson(id, first).andExpect(status().isOk());

            Map<String, Object> stale = validBody("DHA-26");
            stale.put("model", "Lost update");
            stale.put("version", 0);
            putJson(id, stale)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        }

        @Test
        void updateMissing_returns404() throws Exception {
            putJson(987654, validBody("DHA-27")).andExpect(status().isNotFound());
        }

        @Test
        void updateValidatesBody() throws Exception {
            long id = create("DHA-28").get("id").asLong();
            Map<String, Object> body = validBody("DHA-28");
            body.remove("make");
            putJson(id, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.make").exists());
        }

        @Test
        void patchIsNotSupported() throws Exception {
            long id = create("DHA-29").get("id").asLong();
            mvc.perform(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        }
    }

    // ------------------------------------------------------------------ delete

    @Nested
    class Delete {

        @Test
        void deletesAndFreesTheNumber() throws Exception {
            long id = create("DHA-30").get("id").asLong();

            mvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());
            mvc.perform(get(BASE + "/" + id)).andExpect(status().isNotFound());

            // the number can be registered again after deletion
            create("DHA-30");
        }

        @Test
        void deleteMissing_returns404() throws Exception {
            mvc.perform(delete(BASE + "/424242"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }

        @Test
        void deleteTwice_secondIs404() throws Exception {
            long id = create("DHA-31").get("id").asLong();
            mvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());
            mvc.perform(delete(BASE + "/" + id)).andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------------------ availability + misc

    @Nested
    class Availability {

        @Test
        void reportsAvailability() throws Exception {
            long id = create("DHA-40").get("id").asLong();

            mvc.perform(get(BASE + "/availability").param("vehicleNumber", "dha 40"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.available").value(false))
                    .andExpect(jsonPath("$.vehicleNumber").value("DHA 40"));

            mvc.perform(get(BASE + "/availability").param("vehicleNumber", "DHA-40")
                            .param("excludeId", String.valueOf(id)))
                    .andExpect(jsonPath("$.available").value(true));

            mvc.perform(get(BASE + "/availability").param("vehicleNumber", "DHA-41"))
                    .andExpect(jsonPath("$.available").value(true));
        }

        @Test
        void missingParameter_returns400() throws Exception {
            mvc.perform(get(BASE + "/availability"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.vehicleNumber").exists());
        }

        @Test
        void unknownRoute_returnsProblemJson404() throws Exception {
            mvc.perform(get("/api/v1/nope"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }

        @Test
        void corsPreflightFromLocalhostIsAllowed() throws Exception {
            mvc.perform(options(BASE)
                            .header("Origin", "http://localhost:54321")
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", endsWith("54321")));
        }

        @Test
        void corsPreflightFromForeignOriginIsRejected() throws Exception {
            mvc.perform(options(BASE)
                            .header("Origin", "https://evil.example.com")
                            .header("Access-Control-Request-Method", "DELETE"))
                    .andExpect(status().isForbidden());
        }
    }
}
