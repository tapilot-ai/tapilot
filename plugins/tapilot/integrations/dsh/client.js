window.__ModuleLoader__.load({
  id: '@tapilot/dsh',
  factory(require) {
    const React = require('react');
    return {
      inject: ['slots', 'sidebarRight', 'sidebarRightTabs'],
      apply(ctx) {
        const PhonePanel = () => React.createElement('iframe', {
          title: 'Tapilot phone panel', src: '/api/tapilot/panel',
          sandbox: 'allow-scripts allow-same-origin allow-popups',
          style: { display: 'block', border: 0, width: '100%', height: '100%' },
        });
        ctx.effect(() => ctx.sidebarRightTabs.register({
          id: '@tapilot/dsh', kind: 'tapilot', title: () => 'Tapilot',
        }));
        ctx.effect(() => ctx.slots.inject('sidebar.right.pane.tab', () => ctx.slots.register({
          name: 'sidebar.right.pane.tab', key: '@tapilot/dsh',
        }, PhonePanel)));
        ctx.effect(() => ctx.slots.inject('conversation.session.header.actions', () => ctx.slots.register({
          name: 'conversation.session.header.actions', id: 'tapilot', order: 500,
        }, () => React.createElement('button', {
          type: 'button', 'aria-label': 'Tapilot phone panel',
          onClick: () => ctx.sidebarRight.openTab('tapilot'),
          style: { font: 'inherit', color: 'inherit', background: 'transparent', cursor: 'pointer', border: '1px solid currentColor', borderRadius: 8, padding: '6px 12px', minHeight: 36 },
        }, 'Tapilot'))));
        ctx.slots.inject('sidebar.panellist', () => ctx.slots.register({ name: 'sidebar.panellist', id: 'tapilot', label: 'Tapilot', order: 500 }, ({ size = 20 }) =>
          React.createElement('svg', { width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'aria-hidden': true },
            React.createElement('rect', { x: 6, y: 2, width: 12, height: 20, rx: 3 }), React.createElement('path', { d: 'M10 18h4' }))));
        ctx.slots.inject('main', () => ctx.slots.register({ name: 'main', key: 'tapilot' }, PhonePanel));
      },
    };
  },
});
