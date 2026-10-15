import {
  MODEL_PRESET_IDS,
  type ModelConfiguration,
  type ModelPresetId,
  defaultModelConfiguration,
} from './model-presets';

export interface ModelCredentialStore {
  getAll(): Promise<Record<string, string>>;
  set(preset: ModelPresetId, apiKey: string): Promise<void>;
  delete(preset: ModelPresetId): Promise<void>;
}

export interface ModelProfileStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

const NON_SECRET_FIELDS = ['baseUrl', 'modelName', 'modelFamily'] as const;
const PROFILE_PREFIX = 'midscene.modelPresetConfig';

const profileKey = (preset: ModelPresetId, field: string): string =>
  `${PROFILE_PREFIX}.${preset}.${field}`;

export class ModelProfileStore {
  private apiKeys: Partial<Record<ModelPresetId, string>> = {};

  constructor(
    private readonly storage: ModelProfileStorage,
    private readonly credentials: ModelCredentialStore,
  ) {}

  async initialize(): Promise<void> {
    const nativeCredentials = await this.credentials.getAll();
    for (const preset of MODEL_PRESET_IDS) {
      const key = nativeCredentials[preset];
      if (typeof key === 'string') this.apiKeys[preset] = key;
    }

    for (const preset of MODEL_PRESET_IDS) {
      const legacyKey = this.storage.getItem(profileKey(preset, 'apiKey'));
      if (legacyKey === null) continue;
      await this.persistApiKey(preset, legacyKey);
      this.storage.removeItem(profileKey(preset, 'apiKey'));
    }

    const globalLegacyKey = this.storage.getItem('midscene.apiKey');
    if (globalLegacyKey !== null && this.apiKeys.custom === undefined) {
      await this.persistApiKey('custom', globalLegacyKey);
    }
    if (globalLegacyKey !== null) this.storage.removeItem('midscene.apiKey');
  }

  load(preset: ModelPresetId): ModelConfiguration {
    const fallback = defaultModelConfiguration(preset);
    return {
      baseUrl:
        this.storage.getItem(profileKey(preset, 'baseUrl')) ?? fallback.baseUrl,
      apiKey: this.apiKeys[preset] ?? '',
      modelName:
        this.storage.getItem(profileKey(preset, 'modelName')) ??
        fallback.modelName,
      modelFamily:
        this.storage.getItem(profileKey(preset, 'modelFamily')) ??
        fallback.modelFamily,
    };
  }

  async save(
    preset: ModelPresetId,
    configuration: ModelConfiguration,
  ): Promise<void> {
    for (const field of NON_SECRET_FIELDS) {
      this.storage.setItem(profileKey(preset, field), configuration[field]);
    }
    if (configuration.apiKey !== (this.apiKeys[preset] ?? '')) {
      await this.persistApiKey(preset, configuration.apiKey);
    }
  }

  async reset(preset: ModelPresetId): Promise<ModelConfiguration> {
    for (const field of NON_SECRET_FIELDS) {
      this.storage.removeItem(profileKey(preset, field));
    }
    await this.credentials.delete(preset);
    delete this.apiKeys[preset];
    return this.load(preset);
  }

  isConfigured(preset: ModelPresetId): boolean {
    const configuration = this.load(preset);
    return Object.values(configuration).every((value) => value.trim());
  }

  private async persistApiKey(
    preset: ModelPresetId,
    apiKey: string,
  ): Promise<void> {
    if (apiKey) {
      await this.credentials.set(preset, apiKey);
      this.apiKeys[preset] = apiKey;
    } else {
      await this.credentials.delete(preset);
      delete this.apiKeys[preset];
    }
  }
}
