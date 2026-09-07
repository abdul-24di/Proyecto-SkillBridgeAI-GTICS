(function () {
  'use strict';

  var css = `.notif-wrapper { position: relative; display: flex; align-items: center; height: 100%; }
.notification-link { position: relative; display: flex; align-items: center; justify-content: center; height: 100%; }
.notif-panel { display: none; position: absolute; top: calc(100% + 10px); right: 0; width: 360px; background: #ffffff; border: 1px solid #e0e7ef; border-radius: 10px; box-shadow: 0 8px 32px rgba(24,36,51,.14); z-index: 1050; overflow: hidden; color: #1d273b; text-align: left; }
.notif-panel.open { display: block; }
.notif-panel-header { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px 10px; border-bottom: 1px solid #eef0f5; }
.notif-panel-header h6 { margin: 0; font-size: 14px; font-weight: 700; color: #1d273b; display: flex; align-items: center; gap: 6px; }
.notif-mark-all { font-size: 12px; color: #4263eb; text-decoration: none; font-weight: 500; cursor: pointer; background: none; border: none; padding: 0; }
.notif-mark-all:hover { text-decoration: underline; }
.notif-list { max-height: 340px; overflow-y: auto; }
.notif-item { display: flex; gap: 12px; align-items: flex-start; padding: 12px 16px; border-bottom: 1px solid #f0f3f7; cursor: pointer; transition: background .15s; text-decoration: none; }
.notif-item:last-child { border-bottom: none; }
.notif-item:hover { background: #f7f9fc; }
.notif-item.unread { background: #f0f5ff; }
.notif-item.unread:hover { background: #e6edff; }
.notif-icon { flex-shrink: 0; width: 36px; height: 36px; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin-top: 2px; }
.notif-icon svg { width: 17px !important; height: 17px !important; stroke-width: 2px; }
.notif-body { flex: 1; min-width: 0; }
.notif-title { font-size: 13px; font-weight: 600; color: #1d273b; margin-bottom: 2px; line-height: 1.35; }
.notif-desc { font-size: 12px; color: #6b7a99; line-height: 1.4; white-space: normal; }
.notif-time { font-size: 11px; color: #a0aabf; margin-top: 4px; }
.notif-unread-dot { flex-shrink: 0; width: 8px; height: 8px; background: #4263eb; border-radius: 50%; margin-top: 6px; }
.notif-empty { padding: 32px 16px; text-align: center; color: #8592a3; font-size: 13px; }
.notif-panel-footer { padding: 10px 16px; border-top: 1px solid #eef0f5; text-align: center; }
.notif-panel-footer a { font-size: 13px; color: #4263eb; text-decoration: none; font-weight: 500; }
.notif-panel-footer a:hover { text-decoration: underline; }`;

  var style = document.createElement('style');
  style.type = 'text/css';
  style.appendChild(document.createTextNode(css));
  document.head.appendChild(style);

  var NOTIFS = [
    { id: 1, unread: true, titulo: 'Nueva asignacion pendiente', desc: 'Carlos Mendoza ha sido propuesto para el proyecto Clinica AI.', time: 'Hace 5 min', color: 'bg-blue-lt', colorText: 'text-blue', icon: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>' },
    { id: 2, unread: true, titulo: 'Actividad marcada como lista', desc: 'Ana Torres marco "Diseno de pantallas de perfil" como completada.', time: 'Hace 18 min', color: 'bg-yellow-lt', colorText: 'text-yellow', icon: '<path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>' },
    { id: 3, unread: true, titulo: 'Proyecto aprobado por el RM', desc: 'Tu proyecto ERP Cloud fue aprobado y esta ahora Activo.', time: 'Hace 1 hora', color: 'bg-green-lt', colorText: 'text-green', icon: '<path d="M4 4h6l2 2h8v14H4z"/>' },
    { id: 4, unread: false, titulo: 'Solicitud de curso aprobada', desc: 'Tu solicitud para "Spring Boot Avanzado" fue aprobada.', time: 'Hace 3 horas', color: 'bg-azure-lt', colorText: 'text-azure', icon: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>' }
  ];

  function countUnread() { return NOTIFS.filter(function (n) { return n.unread; }).length; }

  function buildPanel() {
    var unread = countUnread();
    var items = NOTIFS.map(function (n) {
      return '<div class="notif-item' + (n.unread ? ' unread' : '') + '" data-id="' + n.id + '"><div class="notif-icon ' + n.color + ' ' + n.colorText + '"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' + n.icon + '</svg></div><div class="notif-body"><div class="notif-title">' + n.titulo + '</div><div class="notif-desc">' + n.desc + '</div><div class="notif-time">' + n.time + '</div></div>' + (n.unread ? '<div class="notif-unread-dot"></div>' : '') + '</div>';
    }).join('');

    return '<div class="notif-panel" id="notifPanel"><div class="notif-panel-header"><h6>Notificaciones' + (unread > 0 ? ' <span class="badge bg-blue">' + unread + '</span>' : '') + '</h6><button class="notif-mark-all" id="notifMarkAll">Marcar todas como leidas</button></div><div class="notif-list" id="notifList">' + (NOTIFS.length === 0 ? '<div class="notif-empty">No tienes notificaciones nuevas</div>' : items) + '</div><div class="notif-panel-footer"><a href="#">Ver todas las notificaciones</a></div></div>';
  }

  function updateDot() {
    var dot = document.querySelector('.notification-dot');
    if (!dot) return;
    dot.style.display = countUnread() > 0 ? '' : 'none';
  }

  function init() {
    var bell = document.querySelector('.notification-link');
    if (!bell) return;

    var wrapper = document.createElement('div');
    wrapper.className = 'notif-wrapper';
    bell.parentNode.insertBefore(wrapper, bell);
    wrapper.appendChild(bell);
    wrapper.insertAdjacentHTML('beforeend', buildPanel());

    var panel = document.getElementById('notifPanel');

    bell.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopPropagation();
      panel.classList.toggle('open');
    });

    document.getElementById('notifMarkAll').addEventListener('click', function () {
      NOTIFS.forEach(function (n) { n.unread = false; });
      var items = document.querySelectorAll('.notif-item');
      items.forEach(function(item) {
          item.classList.remove('unread');
          var dot = item.querySelector('.notif-unread-dot');
          if (dot) dot.remove();
      });
      updateDot();
      var headerH6 = document.querySelector('.notif-panel-header h6');
      if (headerH6) headerH6.innerHTML = 'Notificaciones';
    });

    document.getElementById('notifList').addEventListener('click', function (e) {
      var item = e.target.closest('.notif-item');
      if (!item) return;
      var id = parseInt(item.dataset.id);
      var notif = NOTIFS.find(function (n) { return n.id === id; });
      if (notif) { notif.unread = false; }
      item.classList.remove('unread');
      var dot = item.querySelector('.notif-unread-dot');
      if (dot) dot.remove();
      updateDot();
    });

    document.addEventListener('click', function (e) {
      if (!wrapper.contains(e.target)) {
        panel.classList.remove('open');
      }
    });

    updateDot();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();