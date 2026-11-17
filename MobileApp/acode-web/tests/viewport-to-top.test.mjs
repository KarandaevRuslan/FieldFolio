import test from 'node:test';
import assert from 'node:assert/strict';
import {documentViewport} from '../src/document/viewport.js';

const tick = ms => new Promise(resolve => setTimeout(resolve, ms));

function createReader() {
 const data = new Map();
 const storage = {
  getItem: key => data.get(key) ?? null,
  setItem: (key, value) => data.set(key, value),
 };
 const root = {dataset: {}, style: {overflow: 'auto'}, scrollHeight: 4000};
 const doc = {documentElement: root, body: {}, getElementById: () => ({}), activeElement: null};
 const win = new EventTarget();
 win.innerWidth = 400;
 win.innerHeight = 800;
 win.scrollX = 0;
 win.scrollY = 0;
 win.scrollTo = ({left, top}) => {win.scrollX = left; win.scrollY = top;};
 win.requestAnimationFrame = callback => setTimeout(callback, 0);
 win.ResizeObserver = class {observe() {} disconnect() {}};
 win.MutationObserver = class {observe() {} disconnect() {}};
 const viewport = documentViewport(storage, doc, win);
 return {doc, win, viewport, saved: () => JSON.parse(storage.getItem('fieldfolio:viewport'))};
}

test('toTop cancels ongoing fling, rejects stale scroll events, then accepts the next touch', async () => {
 const {doc, win, viewport, saved} = createReader();
 try {
  viewport.mode(true);
  await tick(10);
  // The user flicks a full document; the browser continues scrolling after touch release.
  win.dispatchEvent(new Event('touchstart'));
  win.scrollTo({left: 0, top: 800});
  win.dispatchEvent(new Event('scroll'));
  assert.equal(saved()?.y ?? 0, 0); // Debounced save is still pending.

  viewport.toTop();
  assert.equal(win.scrollY, 0);
  assert.equal(saved().y, 0);
  assert.equal(doc.documentElement.style.overflow, 'hidden', 'stop native root scrolling');

  // A temporary lock must release itself: the next wheel/swipe must work first try.
  await tick(160);
  assert.equal(doc.documentElement.style.overflow, 'auto');
  // Delayed inertial frames must NOT move the page or overwrite the saved position.
  for (let i = 0; i < 3; i++) {
   win.scrollY = 120 + i * 30;
   win.dispatchEvent(new Event('scroll'));
   assert.equal(win.scrollY, 0);
   await tick(160);
  }
  assert.equal(saved().y, 0);
  win.dispatchEvent(new Event('pointermove'));
  assert.equal(doc.documentElement.style.overflow, 'auto', 'old move is not a new gesture');

  // A NEW user gesture immediately unlocks the document; ordinary scrolling works again.
  win.dispatchEvent(new Event('touchstart'));
  assert.equal(doc.documentElement.style.overflow, 'auto');
  win.scrollY = 340;
  win.dispatchEvent(new Event('scroll'));
  await tick(100);
  assert.equal(saved().y, 340);
 } finally {
  viewport.dispose();
 }
});

test('toTop lock is cleared on mode changes, scrollbar/search controls and dispose', async () => {
 const {doc, win, viewport, saved} = createReader();
 try {
  viewport.mode(true);
  await tick(10);
  viewport.toTop();
  viewport.changedByControl();
  assert.equal(doc.documentElement.style.overflow, 'auto');

  viewport.toTop();
  viewport.changedBySearch();
  assert.equal(doc.documentElement.style.overflow, 'auto');

  viewport.toTop();
  viewport.mode(false);
  assert.equal(doc.documentElement.style.overflow, 'auto');
  assert.equal(saved().y, 0);

  viewport.mode(true);
  await tick(10);
  viewport.toTop();
  win.dispatchEvent(new Event('keydown'));
  assert.equal(doc.documentElement.style.overflow, 'auto');

  viewport.toTop();
  viewport.toTop();
  assert.equal(doc.documentElement.style.overflow, 'hidden');
 } finally {
  viewport.dispose();
  assert.equal(doc.documentElement.style.overflow, 'auto');
 }
});
