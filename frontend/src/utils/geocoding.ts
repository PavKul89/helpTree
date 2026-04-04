export interface GeoLocation {
  lat: number;
  lng: number;
}

const geoCache = new Map<string, GeoLocation | null>();
let lastRequestTime = 0;
const MIN_REQUEST_INTERVAL = 1500;

const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

const CORS_PROXY = 'https://corsproxy.io/?';

const BELARUS_CITIES: Record<string, GeoLocation> = {
  'гомель': { lat: 52.4415, lng: 30.9877 },
  'минск': { lat: 53.9045, lng: 27.5615 },
  'брест': { lat: 52.0976, lng: 23.6881 },
  'гродно': { lat: 53.6694, lng: 23.8131 },
  'витебск': { lat: 55.1909, lng: 30.2049 },
  'могилёв': { lat: 53.9008, lng: 30.3313 },
  'могилев': { lat: 53.9008, lng: 30.3313 },
  'барановичи': { lat: 52.1333, lng: 26.0167 },
  'пинск': { lat: 52.1125, lng: 26.1017 },
  'орша': { lat: 54.5007, lng: 30.4178 },
  'мозырь': { lat: 51.5058, lng: 29.0413 },
  'лида': { lat: 53.8917, lng: 25.3026 },
  'новополоцк': { lat: 55.5317, lng: 28.6425 },
  'светлогорск': { lat: 52.6333, lng: 29.9500 },
  'калинковичи': { lat: 52.2431, lng: 29.3317 },
  'жлобин': { lat: 52.8875, lng: 29.7325 },
  'речица': { lat: 52.3567, lng: 30.4366 },
  'добруш': { lat: 52.4278, lng: 31.3208 },
  'железногорск': { lat: 52.2633, lng: 29.7325 },
  'лепель': { lat: 54.8872, lng: 28.6825 },
  'бобруйск': { lat: 53.1384, lng: 29.2215 },
  'бориславль': { lat: 53.0647, lng: 29.4625 },
  'хойники': { lat: 51.8833, lng: 29.9500 },
  'кричев': { lat: 53.7133, lng: 31.9500 },
  'горки': { lat: 54.2833, lng: 30.9833 },
  'василевичи': { lat: 52.2500, lng: 29.8333 },
  'лунинец': { lat: 52.2500, lng: 26.8000 },
  'петриков': { lat: 52.1333, lng: 28.5000 },
  'корма': { lat: 52.2167, lng: 30.8167 },
  'брагин': { lat: 51.7500, lng: 30.2667 },
  'чаусы': { lat: 53.8167, lng: 31.8667 },
  'россоны': { lat: 55.6333, lng: 28.8167 },
  'миоры': { lat: 55.6167, lng: 27.6333 },
  'дубрава': { lat: 53.5333, lng: 25.4000 },
  'зэльва': { lat: 53.1500, lng: 24.8167 },
  'мосты': { lat: 53.4167, lng: 24.5333 },
  'волковыск': { lat: 53.1667, lng: 24.4500 },
  'пружаны': { lat: 52.5667, lng: 24.4500 },
  'белоозёрск': { lat: 52.4500, lng: 23.8000 },
  'ивацевичи': { lat: 52.7167, lng: 25.3333 },
  'клецк': { lat: 53.0667, lng: 26.6333 },
  'нюнок': { lat: 53.7167, lng: 27.0500 },
  'столбцы': { lat: 53.4833, lng: 26.7333 },
  'марьина горка': { lat: 53.5167, lng: 28.1500 },
  'пуховичи': { lat: 53.5000, lng: 28.2500 },
  'березино': { lat: 53.8333, lng: 28.9833 },
  'червень': { lat: 53.7167, lng: 28.4333 },
  'смолевичи': { lat: 54.0333, lng: 28.0833 },
  'жодино': { lat: 54.1000, lng: 28.3333 },
  'молодечно': { lat: 54.3167, lng: 26.8500 },
  'вилейка': { lat: 54.5000, lng: 26.9167 },
  'сморгонь': { lat: 54.4833, lng: 26.4000 },
  'нарочь': { lat: 54.9833, lng: 26.6833 },
  'заславль': { lat: 54.0000, lng: 27.2833 },
  'логойск': { lat: 54.2000, lng: 27.8500 },
  'березинский': { lat: 54.8333, lng: 28.9833 },
  'крупки': { lat: 54.3167, lng: 29.1333 },
  'богушевск': { lat: 55.0500, lng: 29.7333 },
  'сенно': { lat: 55.8000, lng: 29.8167 },
  'толочин': { lat: 54.6000, lng: 29.7000 },
  'чашники': { lat: 54.3667, lng: 29.1667 },
  'новолукомль': { lat: 55.0167, lng: 29.2167 },
  'руба': { lat: 55.2000, lng: 30.7500 },
  'сураж': { lat: 55.0500, lng: 30.8167 },
  'глубокое': { lat: 55.1333, lng: 27.8167 },
  'поставы': { lat: 55.1167, lng: 26.8333 },
  'шарковщина': { lat: 55.4500, lng: 27.4833 },
  'браслав': { lat: 55.6333, lng: 27.0333 },
  'докшицы': { lat: 54.8833, lng: 27.7667 },
  'бешенковичи': { lat: 55.0500, lng: 28.8000 },
  'верхнедвинск': { lat: 55.7667, lng: 27.9167 },
  'щучин': { lat: 53.6000, lng: 24.7333 },
  'берёзовка': { lat: 54.2333, lng: 25.9667 },
  'краснополье': { lat: 54.3167, lng: 32.4333 },
  'oshmyany': { lat: 54.4167, lng: 25.9167 },
  'микашэвичи': { lat: 52.7833, lng: 27.4667 },
  'любань': { lat: 52.7833, lng: 28.0000 },
  'фаниполь': { lat: 53.7333, lng: 27.6667 },
  'колодищи': { lat: 53.9333, lng: 27.3500 },
  'узда': { lat: 53.4667, lng: 27.2167 },
  'смиловичи': { lat: 53.6333, lng: 27.3000 },
  'ельск': { lat: 51.8167, lng: 28.9667 },
  'наровля': { lat: 51.8000, lng: 29.5167 },
  'октябрьский': { lat: 52.5833, lng: 28.7333 },
  'рогачев': { lat: 53.0667, lng: 30.0500 },
  'буда-кошелево': { lat: 52.7667, lng: 30.5833 },
  'чечерск': { lat: 52.9167, lng: 31.3167 },
  'быхов': { lat: 53.5167, lng: 30.2500 },
  'белыничи': { lat: 54.4167, lng: 30.8167 },
  'круглое': { lat: 54.6333, lng: 29.8167 },
  'мстиславль': { lat: 54.0167, lng: 31.7333 },
  'житковичи': { lat: 52.2167, lng: 27.9667 },
  'славоя': { lat: 52.4500, lng: 29.7000 },
  'малорита': { lat: 51.8167, lng: 24.0167 },
  'овруч': { lat: 51.3167, lng: 28.8167 },
  'мядель': { lat: 54.8833, lng: 26.8167 },
  'копыль': { lat: 53.1500, lng: 27.0833 },
  'несвиж': { lat: 53.2167, lng: 26.6833 },
  'березно': { lat: 51.5667, lng: 28.4500 },
  'дрогичин': { lat: 52.1833, lng: 25.1500 },
  'иваново': { lat: 52.1333, lng: 25.4833 },
  'глуск': { lat: 52.9000, lng: 28.7000 },
  'осиповичи': { lat: 53.3000, lng: 28.8500 },
  'шклов': { lat: 54.2167, lng: 30.9167 },
  'севрюки': { lat: 53.8667, lng: 32.3667 },
  'хиславичи': { lat: 53.9833, lng: 32.2500 },
  'пропойск': { lat: 53.4000, lng: 31.5500 },
  'славгород': { lat: 53.4500, lng: 31.9000 },
  'кировск': { lat: 53.2833, lng: 29.3333 },
  'чериков': { lat: 53.5667, lng: 31.3667 },
  'широкие': { lat: 53.6500, lng: 31.5000 },
  'красная горка': { lat: 53.4167, lng: 31.6667 },
  'поляна': { lat: 54.3000, lng: 30.6000 },
  'победа': { lat: 54.2500, lng: 30.5000 },
  'быков': { lat: 53.8000, lng: 30.3000 },
  'полыковичи': { lat: 53.8833, lng: 30.4000 },
  'сосновка': { lat: 53.8500, lng: 30.2500 },
  'кадино': { lat: 53.8167, lng: 30.2000 },
  'холм': { lat: 55.0500, lng: 31.1833 },
  'холмец': { lat: 54.5333, lng: 30.2000 },
  'гарна': { lat: 54.4500, lng: 30.3500 },
  'халченка': { lat: 54.2833, lng: 29.1833 },
  'свободный': { lat: 55.2833, lng: 28.4500 },
  'куриловичи': { lat: 55.0000, lng: 28.1000 },
  'копище': { lat: 53.9667, lng: 27.5667 },
  'дроздово': { lat: 53.8833, lng: 27.8167 },
  'боровая': { lat: 53.8167, lng: 28.0333 },
  'плиса': { lat: 53.5333, lng: 28.2833 },
  'батуринская': { lat: 54.9833, lng: 30.7167 },
  'лёнва': { lat: 54.9167, lng: 30.5000 },
  'лучеса': { lat: 55.1167, lng: 30.7333 },
  'сиротино': { lat: 55.2000, lng: 30.5500 },
  'шум': { lat: 55.3000, lng: 30.4000 },
  'воропаево': { lat: 55.3167, lng: 30.2500 },
  'дисна': { lat: 55.5667, lng: 28.2333 },
  'дуниловичи': { lat: 55.0167, lng: 26.9167 },
  'коммунары': { lat: 55.0500, lng: 26.6833 },
  'мизгули': { lat: 55.6500, lng: 27.1500 },
  'клястицы': { lat: 55.7167, lng: 28.6167 },
  'валі': { lat: 55.8667, lng: 28.9500 },
  'пралетарская': { lat: 55.8000, lng: 29.1333 },
  'крынкі': { lat: 55.8000, lng: 29.4000 },
  'казьма': { lat: 55.7833, lng: 29.6667 },
  'берёза': { lat: 52.5333, lng: 24.9833 },
  'береза': { lat: 52.5333, lng: 24.9833 },
  'городок': { lat: 55.2833, lng: 29.9833 },
  'прага': { lat: 52.2500, lng: 29.8333 },
  'превалока': { lat: 52.2167, lng: 27.9667 },
  'перавалока': { lat: 52.2167, lng: 27.9667 },
  'peravaloka': { lat: 52.2167, lng: 27.9667 },
};

const normalizeCity = (city: string): string => {
  return city.toLowerCase().trim()
    .replace(/^г\.\s*/, '')
    .replace(/^г\s*/, '')
    .replace(/[.,]/g, '');
};

const findCityInDictionary = (cityName: string): GeoLocation | null => {
  const normalized = normalizeCity(cityName);
  
  if (BELARUS_CITIES[normalized]) {
    return BELARUS_CITIES[normalized];
  }
  
  for (const [key, coords] of Object.entries(BELARUS_CITIES)) {
    if (normalized.includes(key) || key.includes(normalized)) {
      return coords;
    }
  }
  
  return null;
};

export const geocodeCity = async (cityName: string): Promise<GeoLocation | null> => {
  if (!cityName) return null;
  
  const cacheKey = cityName.toLowerCase().trim();
  
  if (geoCache.has(cacheKey)) {
    return geoCache.get(cacheKey) || null;
  }
  
  const fromDictionary = findCityInDictionary(cityName);
  if (fromDictionary) {
    geoCache.set(cacheKey, fromDictionary);
    return fromDictionary;
  }
  
  const now = Date.now();
  const timeSinceLastRequest = now - lastRequestTime;
  if (timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
    await delay(MIN_REQUEST_INTERVAL - timeSinceLastRequest);
  }
  
  try {
    const url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(cityName)}&countrycodes=by&limit=1`;
    
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
