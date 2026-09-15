(function () {
  'use strict';

  const css = `.notif-wrapper{position:relative;display:flex;align-items:center;height:100%}.notification-link{position:relative;display:flex;align-items:center;justify-content:center;height:100%}.notif-panel{display:none;position:absolute;top:calc(100% + 10px);right:0;width:360px;background:#fff;border:1px solid #e0e7ef;border-radius:10px;box-shadow:0 8px 32px rgba(24,36,51,.14);z-index:1050;overflow:hidden;color:#1d273b;text-align:left}.notif-panel.open{display:block}.notif-panel-header{display:flex;align-items:center;justify-content:space-between;padding:14px 16px 10px;border-bottom:1px solid #eef0f5}.notif-panel-header h6{margin:0;font-size:14px;font-weight:700}.notif-mark-all{font-size:12px;color:#4263eb;background:none;border:0}.notif-list{max-height:340px;overflow-y:auto}.notif-item{display:flex;gap:12px;align-items:flex-start;padding:12px 16px;border-bottom:1px solid #f0f3f7;cursor:pointer}.notif-item:hover{background:#f7f9fc}.notif-item.unread{background:#f0f5ff}.notif-icon{flex-shrink:0;width:36px;height:36px;border-radius:50%;display:flex;align-items:center;justify-content:center}.notif-body{flex:1;min-width:0}.notif-title{font-size:13px;font-weight:600}.notif-desc{font-size:12px;color:#6b7a99;line-height:1.4}.notif-time{font-size:11px;color:#a0aabf;margin-top:4px}.notif-unread-dot{width:8px;height:8px;background:#4263eb;border-radius:50%;margin-top:6px}.notif-empty{padding:32px 16px;text-align:center;color:#8592a3;font-size:13px}`;
  const style = document.createElement('style');
  style.textContent = css;
  document.head.appendChild(style);

  let notifications = [];

  function escapeHtml(value) {
    const element = document.createElement('div');
    element.textContent = value || '';
    return element.innerHTML;
  }

  function relativeTime(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    const seconds = Math.round((date.getTime() - Date.now()) / 1000);
    const units = [[86400, 'day'], [3600, 'hour'], [60, 'minute']];
    for (const [size, unit] of units) {
      if (Math.abs(seconds) >= size) {
        return new Intl.RelativeTimeFormat('es', {numeric: 'auto'}).format(Math.round(seconds / size), unit);
      }
    }
    return 'Ahora';
  }

  function iconClass(category) {
    return category === 'CURSO' ? 'bg-azure-lt text-azure' : 'bg-blue-lt text-blue';
  }

  function unreadCount() {
    return notifications.filter(item => !item.leida).length;
  }

  function render(wrapper) {
    const oldPanel = wrapper.querySelector('.notif-panel');
    if (oldPanel) oldPanel.remove();
    const count = unreadCount();
    const items = notifications.map(item => `
      <div class="notif-item${item.leida ? '' : ' unread'}" data-id="${item.id}" data-url="${escapeHtml(item.url)}">
        <div class="notif-icon ${iconClass(item.categoria)}">${item.categoria === 'CURSO' ? 'C' : 'N'}</div>
        <div class="notif-body"><div class="notif-title">${escapeHtml(item.titulo)}</div><div class="notif-desc">${escapeHtml(item.descripcion)}</div><div class="notif-time">${escapeHtml(relativeTime(item.fechaCreacion))}</div></div>
        ${item.leida ? '' : '<div class="notif-unread-dot"></div>'}
      </div>`).join('');
    wrapper.insertAdjacentHTML('beforeend', `<div class="notif-panel" id="notifPanel"><div class="notif-panel-header"><h6>Notificaciones${count ? ` <span class="badge bg-blue">${count}</span>` : ''}</h6><button class="notif-mark-all" type="button">Marcar todas como leídas</button></div><div class="notif-list">${items || '<div class="notif-empty">No tienes notificaciones</div>'}</div></div>`);
    updateDot(wrapper);
  }

  function updateDot(wrapper) {
    const dot = wrapper.querySelector('.notification-dot');
    if (dot) dot.style.display = unreadCount() ? '' : 'none';
  }

  async function request(url) {
    return fetch(url, {method: 'POST', credentials: 'same-origin'});
  }

  async function init() {
    const bell = document.querySelector('.notification-link');
    if (!bell || bell.closest('.notif-wrapper')) return;
    const wrapper = document.createElement('div');
    wrapper.className = 'notif-wrapper';
    bell.parentNode.insertBefore(wrapper, bell);
    wrapper.appendChild(bell);

    try {
      const response = await fetch('/api/notificaciones', {credentials: 'same-origin'});
      if (response.ok) notifications = await response.json();
    } catch (error) {
      notifications = [];
    }
    render(wrapper);

    bell.addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      wrapper.querySelector('.notif-panel')?.classList.toggle('open');
    });

    wrapper.addEventListener('click', async event => {
      const markAll = event.target.closest('.notif-mark-all');
      if (markAll) {
        await request('/api/notificaciones/leer-todas');
        notifications.forEach(item => item.leida = true);
        render(wrapper);
        wrapper.querySelector('.notif-panel')?.classList.add('open');
        return;
      }
      const row = event.target.closest('.notif-item');
      if (!row) return;
      const item = notifications.find(value => String(value.id) === row.dataset.id);
      if (item && !item.leida) {
        await request(`/api/notificaciones/${item.id}/leer`);
        item.leida = true;
      }
      if (row.dataset.url && row.dataset.url !== '#') {
        window.location.href = row.dataset.url;
      } else {
        render(wrapper);
        wrapper.querySelector('.notif-panel')?.classList.add('open');
      }
    });

    document.addEventListener('click', event => {
      if (!wrapper.contains(event.target)) wrapper.querySelector('.notif-panel')?.classList.remove('open');
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
