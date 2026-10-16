/* ============================================
   Musilab Audio - Shared JavaScript
   ============================================ */

// Dynamic copyright year
document.getElementById('copyright-year') && (document.getElementById('copyright-year').textContent = new Date().getFullYear());

// Parallax offset helper
function getOffSet() {
  var _offset = 450;
  var windowHeight = window.innerHeight;
  if (windowHeight > 500) _offset = 400;
  if (windowHeight > 680) _offset = 300;
  if (windowHeight > 830) _offset = 210;
  return _offset;
}

function setParallaxPosition($doc, multiplier, $object) {
  var offset = getOffSet();
  var from_top = $doc.scrollTop(),
    bg_css = 'center ' + (multiplier * from_top - offset) + 'px';
  $object.css({ "background-position": bg_css });
}

// Parallax effect (hero section)
function initParallax() {
  var background_image_parallax = function ($object, multiplier, forceSet) {
    multiplier = typeof multiplier !== 'undefined' ? multiplier : 0.5;
    multiplier = 1 - multiplier;
    var $doc = $(document);
    if (forceSet) {
      setParallaxPosition($doc, multiplier, $object);
    } else {
      $(window).scroll(function () {
        setParallaxPosition($doc, multiplier, $object);
      });
    }
  };

  var background_image_parallax_2 = function ($object, multiplier) {
    multiplier = typeof multiplier !== 'undefined' ? multiplier : 0.5;
    multiplier = 1 - multiplier;
    var $doc = $(document);
    $object.css({ "background-attachment": "fixed" });
    $(window).scroll(function () {
      if ($(window).width() > 768) {
        var firstTop = $object.offset().top,
          pos = $(window).scrollTop(),
          yPos = Math.round((multiplier * (firstTop - pos)) - 186);
        $object.css({ "background-position": 'center ' + yPos + 'px' });
      } else {
        $object.css({ "background-position": "center" });
      }
    });
  };

  background_image_parallax($(".tm-parallax"), 0.30, false);
  background_image_parallax_2($("#about"), 0.80);

  window.addEventListener('resize', function () {
    background_image_parallax($(".tm-parallax"), 0.30, true);
  }, true);
}

// Navbar scroll to solid effect
function initNavbar() {
  $(window).scroll(function (e) {
    if ($(document).scrollTop() > 120) {
      $('.tm-navbar').addClass("scroll");
    } else {
      $('.tm-navbar').removeClass("scroll");
    }
  });
}

// Close mobile nav on link click
function closeNavOnClick() {
  $('#tmNav a').on('click', function () {
    $('.navbar-collapse').removeClass('show');
  });
}

// Lazy loading image fade-in
function initLazyImages() {
  document.querySelectorAll('img[loading="lazy"]').forEach(function (img) {
    if (img.complete) {
      img.classList.add('loaded');
    } else {
      img.addEventListener('load', function () {
        img.classList.add('loaded');
      });
    }
    img.addEventListener('error', function () {
      img.classList.add('loaded');
    });
  });
}

// Scroll reveal animations
function initScrollReveal() {
  if (!('IntersectionObserver' in window)) return;
  var observer = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.1, rootMargin: '0px 0px -50px 0px' });

  document.querySelectorAll('.reveal').forEach(function (el) {
    observer.observe(el);
  });
}

// Initialize all common functionality
$(function () {
  initNavbar();
  closeNavOnClick();
  initLazyImages();
  initScrollReveal();
});
