(function () {
	var topbar = document.getElementById('topbar');
	var onScroll = function () {
		topbar.classList.toggle('scrolled', window.scrollY > 12);
	};
	window.addEventListener('scroll', onScroll, { passive: true });
	onScroll();

	var reveals = document.querySelectorAll('.reveal');
	if ('IntersectionObserver' in window) {
		var observer = new IntersectionObserver(function (entries) {
			entries.forEach(function (entry) {
				if (entry.isIntersecting) {
					entry.target.classList.add('in');
					observer.unobserve(entry.target);
				}
			});
		}, { threshold: 0.15, rootMargin: '0px 0px -40px 0px' });
		reveals.forEach(function (el) { observer.observe(el); });
	} else {
		reveals.forEach(function (el) { el.classList.add('in'); });
	}
})();
