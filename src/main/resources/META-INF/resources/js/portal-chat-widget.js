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

  function mount() {
    if (document.getElementById('portal-chat-root')) {
      return;
    }

    var root = el('div');
    root.id = 'portal-chat-root';
    root.innerHTML =
      '<button type="button" class="pcw-launcher" aria-label="Open AI assistant" title="AI assistant">' +
      '  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">' +
      '    <path d="M5 12c0-4.2 3.4-7.5 7.5-7.5S20 7.8 20 12c0 1.6-.5 3.1-1.4 4.4L20 20l-4-1.3A7.5 7.5 0 0 1 5 12Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/>' +
      '    <circle cx="9.2" cy="12" r="1" fill="currentColor"/>' +
      '    <circle cx="12.5" cy="12" r="1" fill="currentColor"/>' +
      '    <circle cx="15.8" cy="12" r="1" fill="currentColor"/>' +
      '  </svg>' +
      '</button>' +
      '<section class="pcw-panel" role="dialog" aria-label="AI assistant chat" aria-hidden="true">' +
      '  <header class="pcw-header">' +
      '    <div><h2>AI Assistant</h2><p>Control Center help · tools-aware</p></div>' +
      '    <button type="button" class="pcw-close" aria-label="Close chat">✕</button>' +
      '  </header>' +
      '  <div class="pcw-messages" aria-live="polite"></div>' +
      '  <form class="pcw-composer">' +
      '    <textarea name="message" rows="2" placeholder="Ask about ports, RBAC, absent users…" required></textarea>' +
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

    function appendMessage(role, text, meta) {
      var bubble = el('div', 'pcw-msg ' + role, text);
      if (meta) {
        bubble.appendChild(el('span', 'pcw-meta', meta));
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
      appendMessage(
        'assistant',
        'Hi — I can look up portal how-tos and (with permission) absent users. Try “How do ports work?” or “Who was absent today?”.'
      );
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

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', mount);
  } else {
    mount();
  }
})();
