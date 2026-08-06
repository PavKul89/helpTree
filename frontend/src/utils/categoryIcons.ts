import {
  Axe, Trash2, Wrench, Truck, ShoppingCart, ChefHat, Flower2,
  Car, Dog, Baby, Laptop, Scissors, Pill, Scale, BookOpen, GraduationCap,
  CarFront, Home, Sparkles, Package, Heart, Brain, Wifi, Camera, Music,
  Palette, Trophy, Plane, Bird, Plug, Shirt, Apple, Syringe, CreditCard,
  Shield, Building, Pin,
} from 'lucide-react';

export const CATEGORY_ICONS: Record<string, React.ElementType> = {
  'Дрова': Axe,
  'Уборка': Trash2,
  'Ремонт': Wrench,
  'Доставка': Truck,
  'Покупки': ShoppingCart,
  'Готовка': ChefHat,
  'Садоводство': Flower2,
  'Перевозка': Car,
  'Уход за животными': Dog,
  'Помощь с детьми': Baby,
  'Компьютерная помощь': Laptop,
  'Стрижка': Scissors,
  'Медицинская помощь': Pill,
  'Юридическая консультация': Scale,
  'Обучение': BookOpen,
  'Репетитор': GraduationCap,
  'Транспорт': CarFront,
  'Строительство': Home,
  'Клининг': Sparkles,
  'Курьер': Package,
  'Волонтёрство': Heart,
  'Психологическая помощь': Brain,
  'Интернет и связь': Wifi,
  'Фото и видео': Camera,
  'Музыка': Music,
  'Искусство': Palette,
  'Спорт': Trophy,
  'Путешествия': Plane,
  'Питомцы': Bird,
  'Бытовая техника': Plug,
  'Одежда и обувь': Shirt,
  'Продукты': Apple,
  'Аптека': Syringe,
  'Банковские услуги': CreditCard,
  'Страхование': Shield,
  'Недвижимость': Building,
  'Другое': Pin,
};

export const CATEGORIES = Object.keys(CATEGORY_ICONS);

export const getCategoryIcon = (category: string): React.ElementType => {
  return CATEGORY_ICONS[category] || Pin;
};
