/* ==========================================================================
   ShopSphere Presentation Deck — Controller Logic
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {
  // DOM Elements
  const slides = document.querySelectorAll('.slide-item');
  const dots = document.querySelectorAll('.dot');
  const slideCounter = document.getElementById('slideCounter');
  const prevBtn = document.getElementById('prevBtn');
  const nextBtn = document.getElementById('nextBtn');
  const overlayPrevBtn = document.getElementById('overlayPrevBtn');
  const overlayNextBtn = document.getElementById('overlayNextBtn');
  const playPauseBtn = document.getElementById('playPauseBtn');
  const playIcon = document.getElementById('playIcon');
  const playText = document.getElementById('playText');
  const progressBarFill = document.getElementById('progressBarFill');
  const fullscreenBtn = document.getElementById('fullscreenBtn');

  const TOTAL_SLIDES = slides.length;
  const SLIDE_DURATION_MS = 7500; // 7.5 seconds per slide (faster auto-play)

  let currentIndex = 0;
  let isPlaying = false; // PAUSED BY DEFAULT to prevent fast unwanted movement
  let progressInterval = null;
  let progressPercent = 0;

  // Render specific slide by index
  function showSlide(index) {
    if (index < 0) index = TOTAL_SLIDES - 1;
    if (index >= TOTAL_SLIDES) index = 0;

    currentIndex = index;

    // Update slides visibility
    slides.forEach((slide, i) => {
      if (i === currentIndex) {
        slide.classList.add('active');
      } else {
        slide.classList.remove('active');
      }
    });

    // Update dots indicator
    dots.forEach((dot, i) => {
      if (i === currentIndex) {
        dot.classList.add('active');
      } else {
        dot.classList.remove('active');
      }
    });

    // Update counter text
    if (slideCounter) {
      slideCounter.textContent = `Slide ${currentIndex + 1} of ${TOTAL_SLIDES}`;
    }

    // Reset progress bar on manual slide change
    resetProgressBar();
  }

  function nextSlide() {
    showSlide(currentIndex + 1);
  }

  function prevSlide() {
    showSlide(currentIndex - 1);
  }

  // Auto-play timer and progress bar animation
  function startAutoPlay() {
    isPlaying = true;
    if (playIcon) playIcon.textContent = '⏸';
    if (playText) playText.textContent = 'Pause Auto';

    clearInterval(progressInterval);

    progressPercent = 0;
    const stepTime = 100; // Update progress bar every 100ms
    const stepIncrement = (stepTime / SLIDE_DURATION_MS) * 100;

    progressInterval = setInterval(() => {
      if (isPlaying) {
        progressPercent += stepIncrement;
        if (progressBarFill) {
          progressBarFill.style.width = `${Math.min(progressPercent, 100)}%`;
        }

        if (progressPercent >= 100) {
          progressPercent = 0;
          nextSlide();
        }
      }
    }, stepTime);
  }

  function pauseAutoPlay() {
    isPlaying = false;
    if (playIcon) playIcon.textContent = '▶';
    if (playText) playText.textContent = 'Auto Play';
    clearInterval(progressInterval);
    resetProgressBar();
  }

  function toggleAutoPlay() {
    if (isPlaying) {
      pauseAutoPlay();
    } else {
      startAutoPlay();
    }
  }

  function resetProgressBar() {
    progressPercent = 0;
    if (progressBarFill) {
      progressBarFill.style.width = '0%';
    }
  }

  // Event Listeners
  if (nextBtn) nextBtn.addEventListener('click', nextSlide);
  if (prevBtn) prevBtn.addEventListener('click', prevSlide);
  if (overlayNextBtn) overlayNextBtn.addEventListener('click', nextSlide);
  if (overlayPrevBtn) overlayPrevBtn.addEventListener('click', prevSlide);

  if (playPauseBtn) playPauseBtn.addEventListener('click', toggleAutoPlay);

  dots.forEach(dot => {
    dot.addEventListener('click', () => {
      const idx = parseInt(dot.getAttribute('data-index'), 10);
      showSlide(idx);
    });
  });

  // Fullscreen toggle
  if (fullscreenBtn) {
    fullscreenBtn.addEventListener('click', () => {
      if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen().catch(err => {
          console.warn('Error attempting to enable fullscreen:', err.message);
        });
      } else {
        if (document.exitFullscreen) {
          document.exitFullscreen();
        }
      }
    });
  }

  // Keyboard navigation
  document.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowRight' || e.key === 'PageDown') {
      nextSlide();
    } else if (e.key === 'ArrowLeft' || e.key === 'PageUp') {
      prevSlide();
    } else if (e.key === ' ') {
      e.preventDefault();
      toggleAutoPlay();
    }
  });

  // Global Tab Switcher for UI Screenshot vs Code View
  window.switchVisualTab = function(tabBtn, targetId) {
    const parentWrapper = tabBtn.closest('.visual-wrapper');
    if (!parentWrapper) return;

    // Toggle active tab button
    parentWrapper.querySelectorAll('.visual-tab').forEach(b => b.classList.remove('active'));
    tabBtn.classList.add('active');

    // Toggle active content pane
    parentWrapper.querySelectorAll('.visual-tab-pane').forEach(pane => pane.classList.remove('active'));
    const targetPane = parentWrapper.querySelector('#' + targetId);
    if (targetPane) {
      targetPane.classList.add('active');
    }
  };

  // Initialize deck (paused by default so user can read peacefully)
  showSlide(0);
  pauseAutoPlay();
});
