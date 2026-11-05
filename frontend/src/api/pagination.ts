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
 * Normalises a page payload before it reaches component state.
 *
 * A page must never be able to crash a render: while a newer frontend is
 * deployed against an older backend, the same endpoint can still answer with a
 * bare array, and a 304/proxy edge case can yield an empty body. Both shapes
 * collapse into the canonical `PageResponse` here, with a warning so the
 * contract drift stays visible in the console.
 */
export const normalizePage = <T>(
  payload: PageResponse<T> | T[] | null | undefined
): PageResponse<T> => {
  if (Array.isArray(payload)) {
    return {
      content: payload,
      number: 0,
      size: payload.length,
      totalElements: payload.length,
      totalPages: payload.length > 0 ? 1 : 0,
      hasNext: false,
      numberOfElements: payload.length,
      first: true,
      last: true,
      empty: payload.length === 0,
    };
  }

  const page = payload as PageResponse<T> | null | undefined;
  const rawContent = page?.content;
  const content = Array.isArray(rawContent) ? rawContent : [];
  if (!Array.isArray(rawContent)) {
    console.warn('Unexpected page payload, expected content[]:', page);
  }

  return {
    content,
    number: page?.number ?? 0,
    size: page?.size ?? content.length,
    totalElements: page?.totalElements ?? content.length,
    totalPages: page?.totalPages ?? (content.length > 0 ? 1 : 0),
    hasNext: page?.hasNext ?? false,
    numberOfElements: page?.numberOfElements ?? content.length,
    first: page?.first ?? true,
    last: page?.last ?? true,
    empty: page?.empty ?? content.length === 0,
  };
};

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
