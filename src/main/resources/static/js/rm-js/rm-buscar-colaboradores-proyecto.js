document.addEventListener("DOMContentLoaded", () => {
  const cards = [...document.querySelectorAll(".candidate-card")];
  const search = document.getElementById("candidateSearch");
  const availability = document.getElementById("availabilityFilter");
  const load = document.getElementById("loadFilter");
  const empty = document.getElementById("emptyCandidates");

  function render() {
    const term = search.value.trim().toLowerCase();
    const minimum = Number(availability.value);
    let visible = 0;
    cards.forEach(card => {
      const currentLoad = Number(card.dataset.load);
      const maxLoad = Number(card.dataset.max);
      const loadMatches = load.value === "all"
        || (load.value === "available" && currentLoad < maxLoad)
        || (load.value === "full" && currentLoad >= maxLoad);
      const show = card.dataset.search.toLowerCase().includes(term)
        && Number(card.dataset.availability) >= minimum
        && loadMatches;
      card.classList.toggle("d-none", !show);
      if (show) visible++;
    });
    empty.classList.toggle("d-none", visible !== 0);
  }
  search.addEventListener("input", render);
  availability.addEventListener("change", render);
  load.addEventListener("change", render);
  render();
});
