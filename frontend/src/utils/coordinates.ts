import type { Post } from '../types';

/**
 * The map endpoint returns latitude/longitude as nullable values because a
 * post only gets coordinates when the author's profile had them or the
 * backend could geocode the city. Never invent coordinates for the rest.
 */
export const hasCoordinates = (
  post: Post
): post is Post & { latitude: number; longitude: number } => {
  const { latitude, longitude } = post;

  return (
    typeof latitude === 'number' &&
    Number.isFinite(latitude) &&
    latitude >= -90 &&
    latitude <= 90 &&
    typeof longitude === 'number' &&
    Number.isFinite(longitude) &&
    longitude >= -180 &&
    longitude <= 180
  );
};
