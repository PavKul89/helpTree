/**
 * Single pagination contract shared by every large collection endpoint.
 *
 * The backend adds Spring-compatible fields for a smooth migration, but the
 * canonical set is `content/number/size/totalElements/totalPages/hasNext`.
 */
export interface PageResponse<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  numberOfElements: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface PageQuery {
  page?: number;
  size?: number;
}

/** The server clamps `size` to this value; callers should not exceed it. */
export const MAX_PAGE_SIZE = 100;

/**
 * Loads a collection in sequential pages instead of one unbounded response.
 * The page count comes from the server, and a broken/looping server cannot
 * keep the loop alive forever.
 */
export const collectAllPages = async <T>(
  fetchPage: (page: number, size: number) => Promise<PageResponse<T>>,
  size: number = MAX_PAGE_SIZE
): Promise<T[]> => {
  const first = await fetchPage(0, size);
  const items = [...first.content];
  const totalPages = Math.max(1, first.totalPages);

  for (let page = 1; page < totalPages; page += 1) {
    const next = await fetchPage(page, size);
    items.push(...next.content);
    if (!next.hasNext) break;
  }

  return items;
};
