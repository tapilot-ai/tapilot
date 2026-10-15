import { describe, expect, it } from '@rstest/core';
import type { ModelPresetId } from './model-presets';
import {
  type ModelCredentialStore,
  type ModelProfileStorage,
  ModelProfileStore,
} from './model-profile-store';

class MemoryStorage implements ModelProfileStorage {
  readonly values = new Map<string, string>();
  getItem(key: string) {
    return this.values.get(key) ?? null;
  }
  setItem(key: string, value: string) {
    this.values.set(key, value);
  }
  removeItem(key: string) {
    this.values.delete(key);
  }
}

class MemoryCredentials implements ModelCredentialStore {
  readonly values = new Map<ModelPresetId, string>();
  async getAll() {
    return Object.fromEntries(this.values);
  }
  async set(preset: ModelPresetId, apiKey: string) {
    this.values.set(preset, apiKey);
  }
  async delete(preset: ModelPresetId) {
    this.values.delete(preset);
  }
}

const custom = (suffix: string) => ({
  baseUrl: `https://${suffix}.example/v1`,
  apiKey: `key-${suffix}`,
  modelName: `model-${suffix}`,
  modelFamily: `family-${suffix}`,
});

describe('ModelProfileStore', () => {
  it('keeps every field isolated when switching A to B to A', async () => {
    const store = new ModelProfileStore(
      new MemoryStorage(),
      new MemoryCredentials(),
    );
    await store.initialize();
    await store.save('doubao', custom('a'));
    await store.save('qwen', custom('b'));
    expect(store.load('doubao')).toEqual(custom('a'));
    expect(store.load('qwen')).toEqual(custom('b'));
  });

  it('survives an app restart with the same native and web storage', async () => {
    const storage = new MemoryStorage();
    const credentials = new MemoryCredentials();
    const first = new ModelProfileStore(storage, credentials);
    await first.initialize();
    await first.save('custom', custom('restart'));

    const restarted = new ModelProfileStore(storage, credentials);
    await restarted.initialize();
    expect(restarted.load('custom')).toEqual(custom('restart'));
  });

  it('migrates legacy plaintext only after secure persistence succeeds', async () => {
    const storage = new MemoryStorage();
    storage.setItem('midscene.modelPresetConfig.custom.apiKey', 'legacy-key');
    storage.setItem(
      'midscene.modelPresetConfig.custom.baseUrl',
      'https://legacy.example/v1',
    );
    storage.setItem('midscene.modelPresetConfig.custom.modelName', 'legacy');
    storage.setItem('midscene.modelPresetConfig.custom.modelFamily', 'openai');
    storage.setItem('midscene.apiKey', 'older-global-key');
    const credentials = new MemoryCredentials();
    const store = new ModelProfileStore(storage, credentials);

    await store.initialize();

    expect(store.load('custom')).toEqual({
      baseUrl: 'https://legacy.example/v1',
      apiKey: 'legacy-key',
      modelName: 'legacy',
      modelFamily: 'openai',
    });
    expect(credentials.values.get('custom')).toBe('legacy-key');
    expect(
      storage.getItem('midscene.modelPresetConfig.custom.apiKey'),
    ).toBeNull();
    expect(storage.getItem('midscene.apiKey')).toBeNull();
  });

  it('does not delete plaintext when secure migration fails', async () => {
    const storage = new MemoryStorage();
    storage.setItem('midscene.modelPresetConfig.custom.apiKey', 'keep-me');
    const credentials = new MemoryCredentials();
    credentials.set = async () => {
      throw new Error('Keystore unavailable');
    };
    const store = new ModelProfileStore(storage, credentials);

    await expect(store.initialize()).rejects.toThrow('Keystore unavailable');
    expect(storage.getItem('midscene.modelPresetConfig.custom.apiKey')).toBe(
      'keep-me',
    );
  });

  it('migrates the pre-preset global API key to Custom', async () => {
    const storage = new MemoryStorage();
    storage.setItem('midscene.apiKey', 'global-legacy-key');
    const credentials = new MemoryCredentials();
    const store = new ModelProfileStore(storage, credentials);

    await store.initialize();

    expect(store.load('custom').apiKey).toBe('global-legacy-key');
    expect(credentials.values.get('custom')).toBe('global-legacy-key');
    expect(storage.getItem('midscene.apiKey')).toBeNull();
  });

  it('resets only the selected preset', async () => {
    const store = new ModelProfileStore(
      new MemoryStorage(),
      new MemoryCredentials(),
    );
    await store.initialize();
    await store.save('doubao', custom('a'));
    await store.save('qwen', custom('b'));
    await store.reset('doubao');

    expect(store.load('doubao').apiKey).toBe('');
    expect(store.load('qwen')).toEqual(custom('b'));
  });
});
