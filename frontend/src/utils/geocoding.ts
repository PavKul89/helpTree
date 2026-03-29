export interface GeoLocation {
  lat: number;
  lng: number;
}

const geoCache = new Map<string, GeoLocation | null>();
let lastRequestTime = 0;
const MIN_REQUEST_INTERVAL = 1500;

const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

const CORS_PROXY = 'https://corsproxy.io/?';

export const geocodeCity = async (cityName: string): Promise<GeoLocation | null> => {
  const cacheKey = cityName.toLowerCase().trim();
  
  if (geoCache.has(cacheKey)) {
    return geoCache.get(cacheKey) || null;
  }

  const now = Date.now();
  const timeSinceLastRequest = now - lastRequestTime;
  if (timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
    await delay(MIN_REQUEST_INTERVAL - timeSinceLastRequest);
  }
  
  try {
    const url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(cityName)}&limit=1`;
    
    const response = await fetch(CORS_PROXY + encodeURIComponent(url), {
      headers: {
        'User-Agent': 'HelpTree/1.0',
      },
    });
    
    if (response.status === 429 || response.status === 403) {
      console.warn('Nominatim rate limit, returning null');
      geoCache.set(cacheKey, null);
      return null;
    }
    
    const data = await response.json();
    
    lastRequestTime = Date.now();
    
    if (data && data.length > 0) {
      const result = {
        lat: parseFloat(data[0].lat),
        lng: parseFloat(data[0].lon),
      };
      geoCache.set(cacheKey, result);
      return result;
    }
    geoCache.set(cacheKey, null);
    return null;
  } catch (error) {
    console.error('Geocoding error:', error);
    geoCache.set(cacheKey, null);
    return null;
  }
};
