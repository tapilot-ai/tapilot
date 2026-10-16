import assert from 'node:assert/strict';
import test from 'node:test';
import {
  androidAssistantAIActContext,
  mergeAndroidAssistantAIActContext,
} from './assistant-context.mjs';

test('provides localized Android assistant guidance', () => {
  const chinese = androidAssistantAIActContext('zh');
  const english = androidAssistantAIActContext('en');

  assert.match(chinese, /Tapilot/);
  assert.match(chinese, /Launch 动作传入应用名称/);
  assert.match(chinese, /AndroidRecentAppsButton/);
  assert.match(chinese, /AndroidOpenAppDetails/);
  assert.match(chinese, /卸载某个应用/);
  assert.match(chinese, /目标模糊或定位失败时再使用精定位/);
  assert.match(chinese, /禁止无变化地重复同一动作/);
  assert.match(english, /Android phone/);
  assert.match(english, /Launch action with its app name/);
  assert.match(english, /AndroidRecentAppsButton/);
  assert.match(english, /AndroidOpenAppDetails/);
  assert.match(english, /uninstall an app/);
  assert.match(english, /dedicated locate pass/);
  assert.match(english, /Never repeat the same swipe or pinch/);
  assert.match(chinese, /component/);
  assert.match(english, /component/);
});

test('keeps built-in guidance when YAML adds task-specific context', () => {
  assert.equal(
    mergeAndroidAssistantAIActContext('built in', 'use account A', 'en'),
    'built in\n\nAdditional task context:\nuse account A',
  );
  assert.equal(
    mergeAndroidAssistantAIActContext('内置', '使用账号 A', 'zh'),
    '内置\n\n当前任务的额外上下文：\n使用账号 A',
  );
});
