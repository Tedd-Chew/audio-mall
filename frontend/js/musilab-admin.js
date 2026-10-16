/* ============================================
   Musilab Audio - Admin Panel Shared JS
   Toast notifications + auth helpers
   ============================================ */

// Toast notification system
function showToast(message, type) {
  type = type || 'info';
  var container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    document.body.appendChild(container);
  }

  var icons = { success: 'fa-check-circle', error: 'fa-exclamation-circle', info: 'fa-info-circle' };
  var toast = document.createElement('div');
  toast.className = 'toast-notification toast-' + type;
  toast.innerHTML = '<i class="fas ' + (icons[type] || icons.info) + '"></i> ' + message;
  container.appendChild(toast);

  setTimeout(function () {
    toast.classList.add('toast-fadeout');
    setTimeout(function () {
      if (toast.parentNode) toast.parentNode.removeChild(toast);
    }, 300);
  }, 3500);
}

// Auth helpers
function getToken() {
  return localStorage.getItem('token');
}

function checkAuth() {
  return !!(localStorage.getItem('token') && localStorage.getItem('loggedIn') === 'true');
}

function logout() {
  localStorage.removeItem('token');
  localStorage.removeItem('loggedIn');
  window.location.href = '/login.html';
}

// Global AJAX setup: attach Bearer token
$.ajaxSetup({
  beforeSend: function (xhr) {
    var token = getToken();
    if (token) xhr.setRequestHeader('Authorization', 'Bearer ' + token);
  }
});

// Check login on admin page load
if (window.location.pathname.indexOf('admin.html') !== -1) {
  $(function () {
    if (!checkAuth()) {
      window.location.href = '/login.html';
    }
  });
}
