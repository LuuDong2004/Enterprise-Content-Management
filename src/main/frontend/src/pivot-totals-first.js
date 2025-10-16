// Reorder pivot table column totals to appear first (leftmost)
// Runs after client attaches; uses MutationObserver to react to rerenders

const moveTotalsLeft = (root) => {
  try {
    // Find pivot table grid or HTML table
    const grid = root.querySelector('jmix-pivot-table vaadin-grid');
    if (grid) {
      // For vaadin-grid renderer used by PivotTable
      const headerRows = grid.shadowRoot?.querySelectorAll('[part="header-cell"]');
      // No reliable API to move columns; skipping for grid renderer
      return;
    }

    // Fallback: HTML table renderer (pvtTable)
    const pvt = root.querySelector('jmix-pivot-table table.pvtTable');
    if (!pvt) return;

    const headerRow = pvt.querySelector('tr.pvtColHeaders');
    if (!headerRow) return;

    // Identify totals header cells (usually have class pvtTotal or text 'Total')
    const headerCells = Array.from(headerRow.children);
    const totalIndex = headerCells.findIndex((td) => td.classList.contains('pvtTotal') || /tổng|total/i.test(td.textContent || ''));
    if (totalIndex > 0) {
      const totalCell = headerCells[totalIndex];
      headerRow.insertBefore(totalCell, headerCells[0]);

      // Move corresponding data cells for each body row
      const bodyRows = pvt.querySelectorAll('tbody tr');
      bodyRows.forEach((tr) => {
        const cells = Array.from(tr.children);
        const dataCell = cells[totalIndex];
        if (dataCell) tr.insertBefore(dataCell, cells[0]);
      });
    }
  } catch (e) {
    // swallow
  }
};

const setupObserver = () => {
  const root = document;
  const apply = () => moveTotalsLeft(root);
  apply();
  const obs = new MutationObserver(() => apply());
  obs.observe(root.body, { childList: true, subtree: true });
};

if (typeof window !== 'undefined') {
  window.addEventListener('load', setupObserver);
}


