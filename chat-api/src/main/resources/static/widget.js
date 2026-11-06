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
 * Optional data-*: title, subtitle, welcome, placeholder, launcher-label,
 *   primary-color, verified-label, storage-key
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
  var VERIFIED_LABEL = cfg.verifiedLabel || attr('data-verified-label', '');

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

  /**
   * Lightweight Markdown → HTML for assistant replies (escape-first, no HTML pass-through).
   * Supports: headings, bold/italic, inline/code blocks, lists, hr, links, paragraphs.
   */
  function renderMarkdown(src) {
    var text = escapeHtml(src).replace(/\r\n?/g, '\n');
    var blocks = [];
    var inlines = [];

    function stash(store, html) {
      store.push(html);
      return '\0' + (store === blocks ? 'B' : 'I') + (store.length - 1) + '\0';
    }

    text = text.replace(/```([^\n`]*)\n?([\s\S]*?)```/g, function (_, lang, code) {
      return stash(blocks, '<pre class="fts-cw-code"><code>' + code.replace(/^\n+|\n+$/g, '') + '</code></pre>');
    });

    text = text.replace(/`([^`\n]+)`/g, function (_, code) {
      return stash(inlines, '<code>' + code + '</code>');
    });

    text = text.replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, function (_, label, href) {
      return stash(inlines, '<a href="' + href + '" target="_blank" rel="noopener noreferrer">' + label + '</a>');
    });

    text = text.replace(/\*\*\*([^*\n]+)\*\*\*/g, '<strong><em>$1</em></strong>');
    text = text.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>');
    // /m so ^ matches each line start (e.g. "*Path:*" after a blank line)
    text = text.replace(/(^|[^*\n])\*([^*\n]+)\*(?!\*)/gm, '$1<em>$2</em>');
    text = text.replace(/___([^_\n]+)___/g, '<strong><em>$1</em></strong>');
    text = text.replace(/__([^_\n]+)__/g, '<strong>$1</strong>');
    text = text.replace(/(^|[^_\n])_([^_\n]+)_(?!_)/gm, '$1<em>$2</em>');

    var lines = text.split('\n');
    var out = [];
    var listType = null;
    var para = [];

    function closeList() {
      if (listType) {
        out.push('</' + listType + '>');
        listType = null;
      }
    }

    function flushPara() {
      if (!para.length) {
        return;
      }
      out.push('<p>' + para.join('<br>') + '</p>');
      para = [];
    }

    for (var i = 0; i < lines.length; i++) {
      var line = lines[i];
      var trimmed = line.trim();

      if (!trimmed) {
        flushPara();
        closeList();
        continue;
      }

      if (/^\0B\d+\0$/.test(trimmed)) {
        flushPara();
        closeList();
        out.push(trimmed);
        continue;
      }

      var heading = trimmed.match(/^(#{1,3})\s+(.+)$/);
      if (heading) {
        flushPara();
        closeList();
        var level = heading[1].length;
        out.push('<h' + level + '>' + heading[2] + '</h' + level + '>');
        continue;
      }

      if (/^(-{3,}|\*{3,}|_{3,})$/.test(trimmed)) {
        flushPara();
        closeList();
        out.push('<hr>');
        continue;
      }

      var ul = trimmed.match(/^[-*+]\s+(.+)$/);
      var ol = trimmed.match(/^\d+[.)]\s+(.+)$/);
      if (ul || ol) {
        flushPara();
        var type = ul ? 'ul' : 'ol';
        if (listType !== type) {
          closeList();
          out.push('<' + type + '>');
          listType = type;
        }
        out.push('<li>' + (ul ? ul[1] : ol[1]) + '</li>');
        continue;
      }

      closeList();
      para.push(trimmed);
    }

    flushPara();
    closeList();

    text = out.join('');
    text = text.replace(/\0B(\d+)\0/g, function (_, idx) {
      return blocks[Number(idx)] || '';
    });
    text = text.replace(/\0I(\d+)\0/g, function (_, idx) {
      return inlines[Number(idx)] || '';
    });
    return text;
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

  function formatTime(date) {
    var d = date || new Date();
    try {
      return new Intl.DateTimeFormat(undefined, {
        hour: 'numeric',
        minute: '2-digit'
      }).format(d);
    } catch (e) {
      var h = d.getHours();
      var m = d.getMinutes();
      var ampm = h >= 12 ? 'PM' : 'AM';
      h = h % 12 || 12;
      return h + ':' + (m < 10 ? '0' : '') + m + ' ' + ampm;
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
      if (branding.verifiedLabel != null && branding.verifiedLabel !== '') {
        VERIFIED_LABEL = branding.verifiedLabel;
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

    function appendMessage(role, text, options) {
      options = options || {};
      var bubble = el('div', 'fts-cw-msg ' + role);
      if (role === 'assistant') {
        var body = el('div', 'fts-cw-md');
        body.innerHTML = renderMarkdown(text);
        bubble.appendChild(body);
      } else if (role === 'error') {
        bubble.textContent = text;
      } else {
        var userText = el('div', 'fts-cw-text');
        userText.textContent = text;
        bubble.appendChild(userText);
      }

      if (role !== 'error') {
        var footer = el('div', 'fts-cw-footer');
        var showVerified = role === 'assistant' && options.verified !== false
          && (options.verifiedLabel || VERIFIED_LABEL);
        if (showVerified) {
          var label = options.verifiedLabel || VERIFIED_LABEL;
          var verified = el('div', 'fts-cw-verified');
          verified.innerHTML =
            '<span class="fts-cw-verified-icon" aria-hidden="true">' +
            '<svg viewBox="0 0 16 16" width="14" height="14" focusable="false">' +
            '<circle cx="8" cy="8" r="8" fill="currentColor"/>' +
            '<path d="M4.6 8.2l2.1 2.1 4.7-4.7" fill="none" stroke="#fff" stroke-width="1.8" ' +
            'stroke-linecap="round" stroke-linejoin="round"/>' +
            '</svg></span>' +
            '<span>' + escapeHtml(label) + '</span>';
          footer.appendChild(verified);
        }
        footer.appendChild(el('span', 'fts-cw-time', formatTime()));
        bubble.appendChild(footer);
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
      appendMessage('assistant', WELCOME, { verified: false });
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
          appendMessage('assistant', reply);
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
