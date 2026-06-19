// KB content categories — UI-side enumeration used to render the help-center
// chip filter. Kept narrow + ordered (policy, event, promo, guide, brand,
// howto, contact, loyalty). Backend `KnowledgeEntry.category` is typed as a
// plain string in src/types/index.ts to stay tolerant of new categories;
// chip rendering still drives off this fixed set.
export type KnowledgeCategory =
  | 'policy'
  | 'event'
  | 'promo'
  | 'guide'
  | 'brand'
  | 'howto'
  | 'contact'
  | 'loyalty';

export const KNOWLEDGE_CATEGORIES: readonly KnowledgeCategory[] = [
  'policy',
  'event',
  'promo',
  'guide',
  'brand',
  'howto',
  'contact',
  'loyalty',
];
