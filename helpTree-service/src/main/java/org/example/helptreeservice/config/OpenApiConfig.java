package org.example.helptreeservice.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.example.helptreeservice.dto.error.ErrorResponse;
import org.example.helptreeservice.dto.error.FieldErrorDto;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * OpenAPI contract published at {@code /v3/api-docs} and browsable at
 * {@code /swagger-ui/index.html}.
 *
 * <p>The generated document is the source of truth for the frontend: DTO
 * schemas, query parameters, the shared pagination envelope, the error envelope
 * and the bearer security scheme are all described there. See
 * {@code docs/API_CONTRACT.md} for the versioning and client-generation
 * policy.</p>
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String ERROR_SCHEMA_REF = "#/components/schemas/ErrorResponse";

    /** Global error responses that every endpoint can produce. */
    private static final Map<String, String> COMMON_ERROR_RESPONSES = Map.of(
            "400", "Некорректные данные запроса",
            "401", "Требуется авторизация",
            "403", "Нет прав на выполнение операции",
            "404", "Ресурс не найден",
            "409", "Конфликт состояния",
            "429", "Превышен лимит запросов",
            "500", "Внутренняя ошибка сервера"
    );

    @Value("${app.api.version:1}")
    private String apiVersion;

    @Bean
    public OpenAPI helpTreeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("helpTree API")
                        .version(apiVersion)
                        .description("""
                                REST API взаимопомощи helpTree.

                                **Контракт.** Все ошибки возвращаются в едином формате
                                `ErrorResponse` (`status/code/message/traceId/fieldErrors`).
                                Все постраничные списки возвращаются в `PageResponse`
                                (`content/number/size/totalElements/totalPages/hasNext`).

                                **Авторизация.** Защищённые операции используют JWT:
                                `Authorization: Bearer <accessToken>`. Публичные операции
                                помечены как не требующие авторизации.

                                **Трассировка.** Каждый ответ содержит заголовок
                                `X-Request-Id`, совпадающий с полем `traceId` ошибки.
                                """)
                        .license(new License().name("Internal project")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token из /api/auth/login или /api/auth/refresh")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .tags(List.of(
                        new Tag().name("Auth").description("Регистрация, вход, сессии и восстановление доступа"),
                        new Tag().name("Users & Blocks").description("Профили, справочник пользователей и пользовательские блокировки"),
                        new Tag().name("Posts").description("Заявки о помощи"),
                        new Tag().name("Helps & Graph").description("Отклики, история помощи и публичный граф связей"),
                        new Tag().name("Reviews & Ratings").description("Отзывы и рейтинг доверия"),
                        new Tag().name("Chat").description("Личные чаты и сообщения"),
                        new Tag().name("Reports").description("Жалобы пользователей и модерация"),
                        new Tag().name("Notifications").description("Уведомления и настройки доставки"),
                        new Tag().name("Admin — Moderation").description("Административные очереди и модерация"),
                        new Tag().name("Admin — Users").description("Административное управление пользователями"),
                        new Tag().name("Admin — Wallet & Audit").description("Калибровка кошельков и аудит действий"),
                        new Tag().name("Account").description("Самообслуживание аккаунта"),
                        new Tag().name("Achievements").description("Каталог достижений и прогресс пользователя"),
                        new Tag().name("Activity").description("Публичная лента активности пользователя"),
                        new Tag().name("Comments").description("Комментарии к заявкам"),
                        new Tag().name("Media").description("Загрузка и удаление изображений"),
                        new Tag().name("Search & Stats").description("Поиск и публичная статистика"),
                        new Tag().name("Internal").description("Внутренние сервисные endpoints")
                ));
    }

    /**
     * Adds the shared error envelope to every operation. An explicitly declared
     * response (for example 413 on an upload endpoint) always wins.
     */
    @Bean
    public OperationCustomizer commonErrorResponsesCustomizer() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            if (responses == null) {
                responses = new ApiResponses();
                operation.setResponses(responses);
            }
            for (Map.Entry<String, String> entry : COMMON_ERROR_RESPONSES.entrySet()) {
                responses.putIfAbsent(entry.getKey(), errorResponse(entry.getValue()));
            }
            return operation;
        };
    }

    /**
     * The error envelope is produced by a {@code @RestControllerAdvice} and by
     * servlet filters, so it is not the return type of any controller method.
     * Register it explicitly, otherwise the global {@code $ref} would dangle.
     */
    @Bean
    public OpenApiCustomizer errorSchemaCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null) {
                components = new Components();
                openApi.setComponents(components);
            }
            registerSchema(components, ErrorResponse.class);
            registerSchema(components, FieldErrorDto.class);
        };
    }

    private static ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content()
                        .addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                                new MediaType().schema(
                                        new Schema<>().$ref(ERROR_SCHEMA_REF))));
    }

    private static void registerSchema(Components components, Class<?> type) {
        ResolvedSchema resolved = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(type));
        if (resolved == null) {
            return;
        }
        if (resolved.referencedSchemas != null) {
            resolved.referencedSchemas.forEach(components::addSchemas);
        }
        if (resolved.schema != null) {
            components.addSchemas(type.getSimpleName(), resolved.schema);
        }
    }
}
