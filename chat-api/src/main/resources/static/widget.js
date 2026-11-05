/**
 * FTS AI Chat — public embeddable floating widget (vanilla JS).
 *
 * <link rel="stylesheet" href="https://CHAT_HOST/widget.css" />
 * <script
 *   src="https://CHAT_HOST/widget.js"
 *   data-site-key="YOUR_SITE_KEY"
 *   data-api-base="https://CHAT_HOST"
 *   defer
 * ></script>
 *
 * Optional data-*: title, subtitle, welcome, placeholder, launcher-label, primary-color, storage-key
 * Optional override: window.FTS_PUBLIC_CHAT = { ... }
 */
(function () {
  'use strict';

  var script = document.currentScript;
  if (!script) {
    var scripts = document.getElementsByTagName('script');
    for (var i = scripts.length - 1; i >= 0; i--) {
      if ((scripts[i].src || '').indexOf('widget.js') !== -1) {
        script = scripts[i];
        break;
      }
    }
  }

  function attr(name, fallback) {
    if (!script) {
      return fallback;
    }
    var value = script.getAttribute(name);
    return value == null || value === '' ? fallback : value;
  }

  var cfg = window.FTS_PUBLIC_CHAT || {};
  var SITE_KEY = cfg.siteKey || attr('data-site-key', '');
  var API_BASE = (cfg.apiBase || attr('data-api-base', '') || '').replace(/\/$/, '');
  var ENDPOINT = cfg.endpoint || (API_BASE ? API_BASE + '/api/v1/public/chat' : '/api/v1/public/chat');
  var CONFIG_URL = cfg.configUrl || (API_BASE ? API_BASE + '/api/v1/public/config' : '/api/v1/public/config');
  var STORAGE_KEY = cfg.storageKey || attr('data-storage-key', 'fts-public-chat-' + (SITE_KEY || 'default'));

  var TITLE = cfg.title || attr('data-title', 'AI Assistant');
  var SUBTITLE = cfg.subtitle || attr('data-subtitle', 'Ask us anything');
  var WELCOME = cfg.welcome || attr('data-welcome', 'Hi — how can I help you today?');
  var PLACEHOLDER = cfg.placeholder || attr('data-placeholder', 'Type a message…');
  var LAUNCHER_LABEL = cfg.launcherLabel || attr('data-launcher-label', 'AI assistant');
  var PRIMARY_COLOR = cfg.primaryColor || attr('data-primary-color', '');

  if (!SITE_KEY) {
    console.warn('[FTS AI Chat] data-site-key is required on the widget script tag.');
    return;
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) {
      node.className = className;
    }
    if (text != null) {
      node.textContent = text;
    }
    return node;
  }

  function conversationId() {
    try {
      var existing = sessionStorage.getItem(STORAGE_KEY);
      if (existing) {
        return existing;
      }
      var id = (window.crypto && crypto.randomUUID)
        ? crypto.randomUUID()
        : ('fts-' + Date.now() + '-' + Math.random().toString(16).slice(2));
      sessionStorage.setItem(STORAGE_KEY, id);
      return id;
    } catch (e) {
      return 'fts-session';
    }
  }

  function persistConversationId(id) {
    if (!id) {
      return;
    }
    try {
      sessionStorage.setItem(STORAGE_KEY, id);
    } catch (e) { /* ignore */ }
  }

  function applyBranding(root) {
    if (PRIMARY_COLOR) {
      root.style.setProperty('--fts-cw-accent', PRIMARY_COLOR);
      root.style.setProperty('--fts-cw-accent-dim', 'color-mix(in srgb, ' + PRIMARY_COLOR + ' 14%, transparent)');
      root.style.setProperty('--fts-cw-user', 'color-mix(in srgb, ' + PRIMARY_COLOR + ' 18%, transparent)');
    }
  }

  function loadRemoteConfig() {
    var url = CONFIG_URL + (CONFIG_URL.indexOf('?') >= 0 ? '&' : '?') + 'siteKey=' + encodeURIComponent(SITE_KEY);
    return fetch(url, {
      method: 'GET',
      credentials: 'omit',
      headers: { 'Accept': 'application/json' }
    }).then(function (res) {
      if (!res.ok) {
        return null;
      }
      return res.json();
    }).catch(function () {
      return null;
    });
  }

  function mount(branding) {
    if (document.getElementById('fts-chat-root')) {
      return;
    }

    if (branding) {
      TITLE = branding.title || TITLE;
      SUBTITLE = branding.subtitle || SUBTITLE;
      WELCOME = branding.welcome || WELCOME;
      PLACEHOLDER = branding.placeholder || PLACEHOLDER;
      LAUNCHER_LABEL = branding.launcherLabel || LAUNCHER_LABEL;
      if (branding.primaryColor) {
        PRIMARY_COLOR = branding.primaryColor;
      }
    }

    var root = el('div');
    root.id = 'fts-chat-root';
    applyBranding(root);
    root.innerHTML =
      '<button type="button" class="fts-cw-launcher" aria-label="Open ' + escapeHtml(LAUNCHER_LABEL) + '" title="' + escapeHtml(LAUNCHER_LABEL) + '">' +
      '  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">' +
      '    <path d="M5 12c0-4.2 3.4-7.5 7.5-7.5S20 7.8 20 12c0 1.6-.5 3.1-1.4 4.4L20 20l-4-1.3A7.5 7.5 0 0 1 5 12Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/>' +
      '    <circle cx="9.2" cy="12" r="1" fill="currentColor"/>' +
      '    <circle cx="12.5" cy="12" r="1" fill="currentColor"/>' +
      '    <circle cx="15.8" cy="12" r="1" fill="currentColor"/>' +
      '  </svg>' +
      '</button>' +
      '<section class="fts-cw-panel" role="dialog" aria-label="' + escapeHtml(TITLE) + ' chat" aria-hidden="true">' +
      '  <header class="fts-cw-header">' +
      '    <div><h2>' + escapeHtml(TITLE) + '</h2><p>' + escapeHtml(SUBTITLE) + '</p></div>' +
      '    <button type="button" class="fts-cw-close" aria-label="Close chat">✕</button>' +
      '  </header>' +
      '  <div class="fts-cw-messages" aria-live="polite"></div>' +
      '  <form class="fts-cw-composer">' +
      '    <textarea name="message" rows="2" placeholder="' + escapeHtml(PLACEHOLDER) + '" required></textarea>' +
      '    <button type="submit" class="fts-cw-send">Send</button>' +
      '  </form>' +
      '</section>';

    document.body.appendChild(root);

    var launcher = root.querySelector('.fts-cw-launcher');
    var panel = root.querySelector('.fts-cw-panel');
    var closer = root.querySelector('.fts-cw-close');
    var messages = root.querySelector('.fts-cw-messages');
    var form = root.querySelector('.fts-cw-composer');
    var input = form.querySelector('textarea');
    var sendBtn = form.querySelector('.fts-cw-send');
    var busy = false;

    function setOpen(open) {
      panel.classList.toggle('open', open);
      panel.setAttribute('aria-hidden', open ? 'false' : 'true');
      if (open) {
        input.focus();
        scrollBottom();
      }
    }

    function scrollBottom() {
      messages.scrollTop = messages.scrollHeight;
    }

    function appendMessage(role, text, meta) {
      var bubble = el('div', 'fts-cw-msg ' + role, text);
      if (meta) {
        bubble.appendChild(el('span', 'fts-cw-meta', meta));
      }
      messages.appendChild(bubble);
      scrollBottom();
      return bubble;
    }

    function showTyping(show) {
      var existing = messages.querySelector('.fts-cw-typing');
      if (existing) {
        existing.remove();
      }
      if (!show) {
        return;
      }
      var typing = el('div', 'fts-cw-typing');
      typing.setAttribute('aria-label', 'Assistant is typing');
      typing.innerHTML = '<span></span><span></span><span></span>';
      messages.appendChild(typing);
      scrollBottom();
    }

    if (!messages.childElementCount) {
      appendMessage('assistant', WELCOME);
    }

    launcher.addEventListener('click', function () {
      setOpen(!panel.classList.contains('open'));
    });
    closer.addEventListener('click', function () {
      setOpen(false);
    });

    input.addEventListener('keydown', function (event) {
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        form.requestSubmit();
      }
    });

    form.addEventListener('submit', function (event) {
      event.preventDefault();
      if (busy) {
        return;
      }
      var text = (input.value || '').trim();
      if (!text) {
        return;
      }

      appendMessage('user', text);
      input.value = '';
      busy = true;
      sendBtn.disabled = true;
      showTyping(true);

      fetch(ENDPOINT, {
        method: 'POST',
        credentials: 'omit',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        },
        body: JSON.stringify({
          message: text,
          conversationId: conversationId(),
          siteKey: SITE_KEY
        })
      })
        .then(function (res) {
          return res.json().then(function (body) {
            return { ok: res.ok, status: res.status, body: body };
          }).catch(function () {
            return { ok: res.ok, status: res.status, body: null };
          });
        })
        .then(function (result) {
          showTyping(false);
          if (!result.ok) {
            var err = (result.body && (result.body.error || result.body.message))
              || ('Chat request failed (' + result.status + ')');
            appendMessage('error', err);
            return;
          }
          var reply = result.body && result.body.reply ? result.body.reply : '(no reply)';
          if (result.body && result.body.conversationId) {
            persistConversationId(result.body.conversationId);
          }
          var meta = '';
          if (result.body && result.body.provider) {
            meta = 'via ' + result.body.provider + (result.body.fallbackUsed ? ' (fallback)' : '');
          }
          appendMessage('assistant', reply, meta);
        })
        .catch(function (err) {
          showTyping(false);
          appendMessage('error', err && err.message ? err.message : 'Network error talking to the assistant.');
        })
        .finally(function () {
          busy = false;
          sendBtn.disabled = false;
          input.focus();
        });
    });
  }

  function start() {
    loadRemoteConfig().then(function (branding) {
      mount(branding);
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
