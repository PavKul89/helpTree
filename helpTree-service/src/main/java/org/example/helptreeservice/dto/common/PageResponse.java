package org.example.helptreeservice.dto.common;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable pagination envelope shared by the large public/admin collections.
 *
 * <p>The six fields required by the API contract are
 * {@code content/number/size/totalElements/totalPages/hasNext}. The remaining
 * Spring-compatible fields are kept so existing clients can migrate without a
 * second contract change.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(name = "PageResponse", description = "Stable pagination envelope shared by every list endpoint")
public class PageResponse<T> {

    @ArraySchema(arraySchema = @Schema(description = "Page items",
            requiredMode = Schema.RequiredMode.REQUIRED))
    private List<T> content;

    @Schema(description = "Zero-based page index", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private int number;

    @Schema(description = "Requested page size after server clamping", example = "20",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private int size;

    @Schema(description = "Total number of matching items", example = "134",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private long totalElements;

    @Schema(description = "Total number of pages", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
    private int totalPages;

    @Schema(description = "Whether a next page exists", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean hasNext;

    @Schema(description = "Number of items on this page", example = "20")
    private int numberOfElements;

    @Schema(description = "Whether this is the first page", example = "true")
    private boolean first;

    @Schema(description = "Whether this is the last page", example = "false")
    private boolean last;

    @Schema(description = "Whether this page has no items", example = "false")
    private boolean empty;

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .number(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .numberOfElements(page.getNumberOfElements())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }

    /** Empty page with the same envelope shape as a database-backed page. */
    public static <T> PageResponse<T> empty(int number, int size) {
        return PageResponse.<T>builder()
                .content(List.of())
                .number(number)
                .size(size)
                .totalElements(0)
                .totalPages(0)
                .hasNext(false)
                .numberOfElements(0)
                .first(true)
                .last(true)
                .empty(true)
                .build();
    }
}
