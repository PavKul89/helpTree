package org.example.helptreeservice.dto.common;

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
public class PageResponse<T> {

    private List<T> content;
    private int number;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private int numberOfElements;
    private boolean first;
    private boolean last;
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
