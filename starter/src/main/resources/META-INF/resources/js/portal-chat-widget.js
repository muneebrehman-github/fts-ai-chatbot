/**
 * FTS AI Chat — standalone floating widget (vanilla JS).
 * Expects CSRF meta tags: <meta name="_csrf"> and <meta name="_csrf_header">
 * Optional override: window.FTS_AI_CHAT = { endpoint: '/api/v1/chat' }
 */
(function () {
  'use strict';

  var cfg = window.FTS_AI_CHAT || {};
  var ENDPOINT = cfg.endpoint || '/api/v1/chat';
  var STORAGE_KEY = cfg.storageKey || 'fts-ai-chat-conversation-id';
  var TITLE = cfg.title || 'AI Assistant';
  var SUBTITLE = cfg.subtitle || 'Control Center help · tools-aware';
  var WELCOME = cfg.welcome
    || 'Hi — I can look up portal how-tos and (with permission) absent users. Try “How do ports work?” or “Who was absent today?”.';
  var PLACEHOLDER = cfg.placeholder || 'Ask about ports, RBAC, absent users…';
  var LAUNCHER_LABEL = cfg.launcherLabel || 'AI assistant';
  var VERIFIED_LABEL = cfg.verifiedLabel || '';

  function csrfHeaders() {
    var tokenMeta = document.querySelector('meta[name="_csrf"]');
    var headerMeta = document.querySelector('meta[name="_csrf_header"]');
    var headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
    if (tokenMeta && headerMeta) {
      headers[headerMeta.getAttribute('content')] = tokenMeta.getAttribute('content');
    }
    return headers;
  }

  function conversationId() {
    try {
      var existing = sessionStorage.getItem(STORAGE_KEY);
      if (existing) {
        return existing;
      }
      var id = (window.crypto && crypto.randomUUID)
        ? crypto.randomUUID()
        : ('cc-' + Date.now() + '-' + Math.random().toString(16).slice(2));
      sessionStorage.setItem(STORAGE_KEY, id);
      return id;
    } catch (e) {
      return 'cc-session';
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
      return stash(blocks, '<pre class="pcw-code"><code>' + code.replace(/^\n+|\n+$/g, '') + '</code></pre>');
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

  function mount() {
    if (document.getElementById('portal-chat-root')) {
      return;
    }

    var root = el('div');
    root.id = 'portal-chat-root';
    root.innerHTML =
      '<button type="button" class="pcw-launcher" aria-label="Open ' + escapeHtml(LAUNCHER_LABEL) + '" title="' + escapeHtml(LAUNCHER_LABEL) + '">' +
      '  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">' +
      '    <path d="M5 12c0-4.2 3.4-7.5 7.5-7.5S20 7.8 20 12c0 1.6-.5 3.1-1.4 4.4L20 20l-4-1.3A7.5 7.5 0 0 1 5 12Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/>' +
      '    <circle cx="9.2" cy="12" r="1" fill="currentColor"/>' +
      '    <circle cx="12.5" cy="12" r="1" fill="currentColor"/>' +
      '    <circle cx="15.8" cy="12" r="1" fill="currentColor"/>' +
      '  </svg>' +
      '</button>' +
      '<section class="pcw-panel" role="dialog" aria-label="' + escapeHtml(TITLE) + ' chat" aria-hidden="true">' +
      '  <header class="pcw-header">' +
      '    <div><h2>' + escapeHtml(TITLE) + '</h2><p>' + escapeHtml(SUBTITLE) + '</p></div>' +
      '    <button type="button" class="pcw-close" aria-label="Close chat">✕</button>' +
      '  </header>' +
      '  <div class="pcw-messages" aria-live="polite"></div>' +
      '  <form class="pcw-composer">' +
      '    <textarea name="message" rows="2" placeholder="' + escapeHtml(PLACEHOLDER) + '" required></textarea>' +
      '    <button type="submit" class="pcw-send">Send</button>' +
      '  </form>' +
      '</section>';

    document.body.appendChild(root);

    var launcher = root.querySelector('.pcw-launcher');
    var panel = root.querySelector('.pcw-panel');
    var closer = root.querySelector('.pcw-close');
    var messages = root.querySelector('.pcw-messages');
    var form = root.querySelector('.pcw-composer');
    var input = form.querySelector('textarea');
    var sendBtn = form.querySelector('.pcw-send');
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
      var bubble = el('div', 'pcw-msg ' + role);
      if (role === 'assistant') {
        var body = el('div', 'pcw-md');
        body.innerHTML = renderMarkdown(text);
        bubble.appendChild(body);
      } else if (role === 'error') {
        bubble.textContent = text;
      } else {
        var userText = el('div', 'pcw-text');
        userText.textContent = text;
        bubble.appendChild(userText);
      }

      if (role !== 'error') {
        var footer = el('div', 'pcw-footer');
        var showVerified = role === 'assistant' && options.verified !== false
          && (options.verifiedLabel || VERIFIED_LABEL);
        if (showVerified) {
          var label = options.verifiedLabel || VERIFIED_LABEL;
          var verified = el('div', 'pcw-verified');
          verified.innerHTML =
            '<span class="pcw-verified-icon" aria-hidden="true">' +
            '<svg viewBox="0 0 16 16" width="14" height="14" focusable="false">' +
            '<circle cx="8" cy="8" r="8" fill="currentColor"/>' +
            '<path d="M4.6 8.2l2.1 2.1 4.7-4.7" fill="none" stroke="#fff" stroke-width="1.8" ' +
            'stroke-linecap="round" stroke-linejoin="round"/>' +
            '</svg></span>' +
            '<span>' + escapeHtml(label) + '</span>';
          footer.appendChild(verified);
        }
        footer.appendChild(el('span', 'pcw-time', formatTime()));
        bubble.appendChild(footer);
      }

      messages.appendChild(bubble);
      scrollBottom();
      return bubble;
    }

    function showTyping(show) {
      var existing = messages.querySelector('.pcw-typing');
      if (existing) {
        existing.remove();
      }
      if (!show) {
        return;
      }
      var typing = el('div', 'pcw-typing');
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
        credentials: 'same-origin',
        headers: csrfHeaders(),
        body: JSON.stringify({
          message: text,
          conversationId: conversationId()
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

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', mount);
  } else {
    mount();
  }
})();
