import { describe, expect, it } from '@rstest/core';
import {
  classifyModelConnectionResponse,
  modelConnectionEndpoint,
} from './model-connection';

describe('model connection', () => {
  it('adds the OpenAI chat completions path to a base URL', () => {
    expect(modelConnectionEndpoint('https://example.com/v1')).toBe(
      'https://example.com/v1/chat/completions',
    );
  });

  it('completes a base URL whose path ends in chat', () => {
    expect(modelConnectionEndpoint('https://example.com/chat')).toBe(
      'https://example.com/chat/completions',
    );
  });

  it('keeps complete custom endpoints intact', () => {
    expect(
      modelConnectionEndpoint('https://example.com/chat/completions'),
    ).toBe('https://example.com/chat/completions');
    expect(modelConnectionEndpoint('https://example.com/v1/responses')).toBe(
      'https://example.com/v1/responses',
    );
  });

  it('classifies actionable service failures', () => {
    expect(classifyModelConnectionResponse(401, '')).toBe('apiKey');
    expect(classifyModelConnectionResponse(400, 'model is unavailable')).toBe(
      'model',
    );
    expect(classifyModelConnectionResponse(429, '')).toBe('quota');
    expect(classifyModelConnectionResponse(500, '')).toBe('request');
  });
});
