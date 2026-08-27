import { mount } from '@vue/test-utils';
import { afterEach, describe, expect, it } from 'vitest';
import NetworkStatusBanner from './NetworkStatusBanner.vue';

function setOnline(value: boolean) {
  Object.defineProperty(window.navigator, 'onLine', {
    configurable: true,
    value
  });
}

describe('NetworkStatusBanner', () => {
  afterEach(() => setOnline(true));

  it('announces when the browser is offline', async () => {
    setOnline(true);
    const wrapper = mount(NetworkStatusBanner);
    expect(wrapper.text()).toBe('');

    setOnline(false);
    window.dispatchEvent(new Event('offline'));
    await wrapper.vm.$nextTick();

    expect(wrapper.get('[role="status"]').text()).toContain('网络连接已中断');
  });
});
