(() => {
  'use strict';

  const API_BASE = 'http://localhost:8080/api';
  const LETTERS = ['A', 'B', 'C', 'D'];
  const QUIZ_COUNT = 5;
  const FLASH_COUNT = 8;
  const MAX_CHARS = 20000;

  const $ = (id) => document.getElementById(id);

  const notesEl = $('notes');
  const charCountEl = $('charCount');
  const btnSummary = $('btnSummary');
  const btnQuiz = $('btnQuiz');
  const btnFlash = $('btnFlash');
  const allButtons = [btnSummary, btnQuiz, btnFlash];

  const sections = { summary: $('summarySection'), quiz: $('quizSection'), flash: $('flashSection') };
  const statusEls = { summary: $('summaryStatus'), quiz: $('quizStatus'), flash: $('flashStatus') };

  const summaryOutput = $('summaryOutput');
  const btnCopy = $('btnCopy');
  const quizOutput = $('quizOutput');
  const flashGrid = $('flashGrid');
  const flashProgress = $('flashProgress');
  const btnRestart = $('btnRestart');

  let summaryRaw = '';

  // ---------------------------------------------------------------- helpers

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function setStatus(node, message, type) {
    node.textContent = message || '';
    node.className = 'status' + (type ? ' ' + type : '');
    node.hidden = !message;
  }

  function setBusy(busy) {
    allButtons.forEach((b) => { b.disabled = busy; });
    document.body.setAttribute('aria-busy', String(busy));
  }

  async function post(path, notes) {
    let res;
    try {
      res = await fetch(`${API_BASE}/${path}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ notes })
      });
    } catch (err) {
      throw new Error(
        'Cannot reach the backend at http://localhost:8080. Make sure it is running, and that this page is ' +
        'opened from http://127.0.0.1:5500 (localhost and 127.0.0.1 are different origins for CORS).'
      );
    }
    let data = null;
    try { data = await res.json(); } catch (err) { /* handled below */ }
    if (!res.ok) {
      throw new Error((data && data.error) || `The server responded with an error (HTTP ${res.status}).`);
    }
    if (data === null) throw new Error('The server returned a response that could not be read.');
    return data;
  }

  async function run(key, task) {
    const section = sections[key];
    const status = statusEls[key];
    section.hidden = false;
    const notes = notesEl.value.trim();
    if (!notes) {
      setStatus(status, 'Please paste some study notes first.', 'error');
      notesEl.focus();
      return;
    }
    setBusy(true);
    setStatus(status, 'Generating… this can take up to a minute.', 'loading');
    try {
      await task(notes);
      setStatus(status, '');
    } catch (err) {
      setStatus(status, err.message || 'Something went wrong.', 'error');
    } finally {
      setBusy(false);
    }
  }

  // ---------------------------------------------------------------- notes input

  function updateCount() {
    charCountEl.textContent = `${notesEl.value.length.toLocaleString()} / ${MAX_CHARS.toLocaleString()} characters`;
  }
  notesEl.addEventListener('input', updateCount);
  updateCount();

  // ---------------------------------------------------------------- safe Markdown renderer

  function addInline(parent, text) {
    text.split(/\*\*(.+?)\*\*/g).forEach((part, i) => {
      if (!part) return;
      if (i % 2 === 1) {
        parent.appendChild(el('strong', '', part));
      } else {
        parent.appendChild(document.createTextNode(part));
      }
    });
  }

  function renderMarkdown(container, text) {
    container.replaceChildren();
    let list = null;
    let listType = null;
    let para = [];

    const flushPara = () => {
      if (para.length) {
        const p = el('p');
        addInline(p, para.join(' '));
        container.appendChild(p);
        para = [];
      }
    };
    const closeList = () => { list = null; listType = null; };
    const addItem = (type, content) => {
      if (listType !== type) {
        list = el(type);
        container.appendChild(list);
        listType = type;
      }
      const li = el('li');
      addInline(li, content);
      list.appendChild(li);
    };

    text.split(/\r?\n/).forEach((raw) => {
      const line = raw.trim();
      let m;
      if (!line) {
        flushPara(); closeList();
      } else if ((m = line.match(/^(#{1,6})\s+(.*)$/))) {
        flushPara(); closeList();
        const h = el('h' + Math.min(m[1].length + 2, 6));
        addInline(h, m[2]);
        container.appendChild(h);
      } else if ((m = line.match(/^[-*•]\s+(.*)$/))) {
        flushPara();
        addItem('ul', m[1]);
      } else if ((m = line.match(/^\d+[.)]\s+(.*)$/))) {
        flushPara();
        addItem('ol', m[1]);
      } else if (/^(-{3,}|\*{3,})$/.test(line)) {
        flushPara(); closeList();
        container.appendChild(el('hr'));
      } else {
        closeList();
        para.push(line);
      }
    });
    flushPara();
  }

  // ---------------------------------------------------------------- summary

  btnSummary.addEventListener('click', () => run('summary', async (notes) => {
    const data = await post('notes', notes);
    if (typeof data.summary !== 'string' || !data.summary.trim()) {
      throw new Error('The server did not return a summary.');
    }
    summaryRaw = data.summary;
    renderMarkdown(summaryOutput, summaryRaw);
    btnCopy.hidden = false;
    btnCopy.textContent = 'Copy Summary';
  }));

  btnCopy.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(summaryRaw);
      btnCopy.textContent = 'Copied!';
    } catch (err) {
      btnCopy.textContent = 'Copy failed — select the text manually';
    }
    setTimeout(() => { btnCopy.textContent = 'Copy Summary'; }, 2500);
  });

  // ---------------------------------------------------------------- quiz

  function validateQuiz(data) {
    const bad = () => new Error('The server returned a quiz in an unexpected format. Please try again.');
    if (!data || !Array.isArray(data.questions) || data.questions.length !== QUIZ_COUNT) throw bad();
    data.questions.forEach((q) => {
      const ok = q && typeof q.question === 'string' && q.options &&
        LETTERS.every((l) => typeof q.options[l] === 'string') &&
        LETTERS.includes(q.answer) && typeof q.explanation === 'string';
      if (!ok) throw bad();
    });
  }

  function renderQuiz(data) {
    const state = { submitted: false };
    quizOutput.replaceChildren();

    const fieldsets = data.questions.map((q, i) => {
      const fs = el('fieldset', 'q-card');
      fs.appendChild(el('legend', '', `${i + 1}. ${q.question}`));
      const labels = {};
      LETTERS.forEach((letter) => {
        const label = el('label', 'option');
        const input = document.createElement('input');
        input.type = 'radio';
        input.name = `q${i}`;
        input.value = letter;
        label.appendChild(input);
        label.appendChild(el('span', '', `${letter}. ${q.options[letter]}`));
        fs.appendChild(label);
        labels[letter] = label;
      });
      quizOutput.appendChild(fs);
      return { fs, labels };
    });

    const actions = el('div', 'quiz-actions');
    const submit = el('button', 'btn btn-primary', 'Submit Quiz');
    submit.type = 'button';
    const message = el('p', 'inline-msg');
    message.setAttribute('role', 'alert');
    const score = el('p', 'score');
    score.setAttribute('role', 'status');
    score.hidden = true;
    const retake = el('button', 'btn btn-secondary', 'Try Again');
    retake.type = 'button';
    retake.hidden = true;
    const fresh = el('button', 'btn btn-secondary', 'Generate New Quiz');
    fresh.type = 'button';
    fresh.hidden = true;
    actions.append(submit, message, score, retake, fresh);
    quizOutput.appendChild(actions);

    submit.addEventListener('click', () => {
      if (state.submitted) return;
      const picked = data.questions.map((_, i) => {
        const checked = quizOutput.querySelector(`input[name="q${i}"]:checked`);
        return checked ? checked.value : null;
      });
      if (picked.some((p) => p === null)) {
        message.textContent = 'Please answer all five questions before submitting.';
        return;
      }
      state.submitted = true;
      message.textContent = '';
      let correct = 0;

      data.questions.forEach((q, i) => {
        const { fs, labels } = fieldsets[i];
        const isRight = picked[i] === q.answer;
        if (isRight) correct += 1;
        labels[q.answer].classList.add('correct');
        if (!isRight) labels[picked[i]].classList.add('wrong');
        fs.querySelectorAll('input').forEach((inp) => { inp.disabled = true; });

        const fb = el('p', 'feedback ' + (isRight ? 'ok' : 'no'));
        fb.appendChild(el('strong', '', isRight ? 'Correct. ' : 'Incorrect. '));
        fb.appendChild(document.createTextNode(
          `Correct answer: ${q.answer}. ${q.options[q.answer]} — ${q.explanation}`
        ));
        fs.appendChild(fb);
      });

      submit.disabled = true;
      score.textContent = `${correct} out of ${QUIZ_COUNT} correct`;
      score.hidden = false;
      retake.hidden = false;
      fresh.hidden = false;
      score.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    });

    retake.addEventListener('click', () => renderQuiz(data));
    fresh.addEventListener('click', () => btnQuiz.click());
  }

  btnQuiz.addEventListener('click', () => run('quiz', async (notes) => {
    const data = await post('quiz', notes);
    validateQuiz(data);
    renderQuiz(data);
  }));

  // ---------------------------------------------------------------- flashcards

  function validateFlashcards(data) {
    const bad = () => new Error('The server returned flashcards in an unexpected format. Please try again.');
    if (!data || !Array.isArray(data.flashcards) || data.flashcards.length !== FLASH_COUNT) throw bad();
    data.flashcards.forEach((c) => {
      if (!c || typeof c.question !== 'string' || !c.question.trim() ||
          typeof c.answer !== 'string' || !c.answer.trim()) throw bad();
    });
  }

  function renderFlashcards(data) {
    // One shared, mutable state object per card. UI code mutates these objects directly.
    const cards = data.flashcards.map((c) => ({
      question: c.question.trim(),
      answer: c.answer.trim(),
      revealed: false,
      status: null, // null | 'known' | 'review'
      refresh: () => {}
    }));

    const known = $('knownCount');
    const review = $('reviewCount');
    const remaining = $('remainingCount');

    function updateProgress() {
      const k = cards.filter((c) => c.status === 'known').length;
      const r = cards.filter((c) => c.status === 'review').length;
      known.textContent = String(k);
      review.textContent = String(r);
      remaining.textContent = String(cards.length - k - r);
    }

    flashGrid.replaceChildren();
    cards.forEach((card, i) => {
      const art = el('article', 'flashcard');
      art.setAttribute('aria-label', `Flashcard ${i + 1}`);
      art.append(el('p', 'fc-label', `Question ${i + 1}`), el('p', 'fc-text', card.question));

      const answerBox = el('div', 'fc-answer');
      answerBox.append(el('p', 'fc-label', 'Answer'), el('p', 'fc-text', card.answer));

      const show = el('button', 'btn btn-secondary', 'Show Answer');
      show.type = 'button';

      const actions = el('div', 'fc-actions');
      const knowBtn = el('button', 'btn btn-good', 'Know It');
      knowBtn.type = 'button';
      const reviewBtn = el('button', 'btn btn-warn', 'Review Again');
      reviewBtn.type = 'button';
      actions.append(knowBtn, reviewBtn);

      art.append(show, answerBox, actions);
      flashGrid.appendChild(art);

      card.refresh = () => {
        answerBox.hidden = !card.revealed;
        actions.hidden = !card.revealed;
        show.hidden = card.revealed;
        art.classList.toggle('known', card.status === 'known');
        art.classList.toggle('review', card.status === 'review');
        knowBtn.setAttribute('aria-pressed', String(card.status === 'known'));
        reviewBtn.setAttribute('aria-pressed', String(card.status === 'review'));
      };

      show.addEventListener('click', () => { card.revealed = true; card.refresh(); });
      knowBtn.addEventListener('click', () => { card.status = 'known'; card.refresh(); updateProgress(); });
      reviewBtn.addEventListener('click', () => { card.status = 'review'; card.refresh(); updateProgress(); });

      card.refresh();
    });

    btnRestart.onclick = () => {
      cards.forEach((c) => { c.revealed = false; c.status = null; c.refresh(); });
      updateProgress();
    };

    flashProgress.hidden = false;
    btnRestart.hidden = false;
    updateProgress();
  }

  btnFlash.addEventListener('click', () => run('flash', async (notes) => {
    const data = await post('flashcards', notes);
    validateFlashcards(data);
    renderFlashcards(data);
  }));
})();
