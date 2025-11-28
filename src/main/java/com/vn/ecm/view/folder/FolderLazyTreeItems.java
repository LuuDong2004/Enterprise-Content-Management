package com.vn.ecm.view.folder;

import com.vaadin.flow.data.provider.AbstractDataProvider;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalDataProvider;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery;
import com.vaadin.flow.shared.Registration;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.SourceStorage;
import io.jmix.core.*;
import io.jmix.core.entity.EntityValues;
import io.jmix.core.metamodel.model.MetaClass;
import io.jmix.core.metamodel.model.MetaPropertyPath;
import io.jmix.flowui.data.BindingState;
import io.jmix.flowui.data.grid.DataGridItems;
import io.jmix.flowui.kit.event.EventBus;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import java.util.function.Consumer;
import java.util.stream.Stream;

public class FolderLazyTreeItems extends AbstractDataProvider<Folder, Void>
        implements HierarchicalDataProvider<Folder, Void>, DataGridItems.Sortable<Folder> {

    private Folder selectedItem;
    private Sort sort = Sort.UNSORTED;

    private EventBus eventBus;

    private final DataManager dataManager;

    private final Metadata metadata;

    private String conditions;

    private SourceStorage storage;

    public FolderLazyTreeItems(DataManager dataManager, Metadata metadata , String conditions , SourceStorage storage) {
        this.dataManager = dataManager;
        this.metadata = metadata;
        this.conditions = conditions;
        this.storage = storage;
    }

    @Override
    public int getChildCount(HierarchicalQuery<Folder, Void> query) {
        return Math.toIntExact(getItems(query).size());
    }

    @Override
    public Stream<Folder> fetchChildren(HierarchicalQuery<Folder, Void> query) {
        Collection<Folder> list = getItems(query);
//            notifications.create(String.format("Loaded: %d items", list.size()))
//                    .withPosition(Notification.Position.TOP_CENTER)
//                    .withThemeVariant(NotificationVariant.LUMO_SUCCESS)
//                    .show();

        return list.stream();
    }

    protected Collection<Folder> getItems(HierarchicalQuery<Folder, Void> query) {
        boolean root = (query.getParent() == null);

        String baseConditions = root
                ? "e.parent is null"
                : "e.parent = :parent";

        String sql = "select e from Folder e where " + baseConditions + conditions +
                " order by e.id" ;

        var loader = dataManager.load(Folder.class)
                .query(sql)
                .sort(sort)
                .firstResult(query.getOffset())
                .maxResults(query.getLimit());

        if (!root) {
            loader.parameter("parent", query.getParent());
        }
        loader.parameter("storage", storage);

        return loader.list();
    }
    @Override
    public Collection<Folder> getItems() {
        // Trả về danh sách root folder (parent is null) cho TreeDataGrid.
        // Các node con sẽ được load lazy qua fetchChildren(getItems(HierarchicalQuery...)).
        String sql = "select e from Folder e where e.parent is null" + conditions + " order by e.id";
        return dataManager.load(Folder.class)
                .query(sql)
                .parameter("storage", storage)
                .sort(sort)
                .list();
    }

    @Nullable
    @Override
    public Folder getItem(@Nullable Object itemId) {
        return itemId == null
                ? null
                : dataManager.load(Id.of(itemId, Folder.class)).one();
    }

    @Override
    public Object getItemValue(Object itemId, MetaPropertyPath propertyId) {
        return EntityValues.getValueEx(getItem(itemId), propertyId);
    }

    @Override
    public Folder getSelectedItem() {
        return selectedItem;
    }

    @Override
    public void setSelectedItem(Folder item) {
        this.selectedItem = item;

        getEventBus().fireEvent(new SelectedItemChangeEvent<>(this, item));
    }

    @Override
    public void sort(Object[] propertyId, boolean[] ascending) {
        sort = createSort(propertyId, ascending);
    }

    @Override
    public void resetSortOrder() {
        sort = Sort.UNSORTED;
    }

    private Sort createSort(Object[] propertyId, boolean[] ascending) {
        List<Sort.Order> orders = new ArrayList<>();

        for (int i = 0; i < propertyId.length; i++) {
            String property;
            if (propertyId[i] instanceof MetaPropertyPath) {
                property = ((MetaPropertyPath) propertyId[i]).toPathString();
            } else {
                property = (String) propertyId[i];
            }

            Sort.Order order = ascending[i]
                    ? Sort.Order.asc(property)
                    : Sort.Order.desc(property);

            orders.add(order);
        }
        return Sort.by(orders);
    }

    @Override
    public boolean hasChildren(Folder item) {
        String sql = "select e from Folder e where e.parent = :parent " + conditions;
        LoadContext<Object> loadContext = new LoadContext<>(getEntityMetaClass())
                .setQuery(new LoadContext.Query(sql)
                        .setParameter("parent", item)
                        .setParameter("storage", storage))
                ;

        return dataManager.getCount(loadContext) > 0;
    }

    public MetaClass getEntityMetaClass() {
        return metadata.getClass(Folder.class);
    }

    @Override
    public Registration addStateChangeListener(Consumer<StateChangeEvent> listener) {
        return getEventBus().addListener(StateChangeEvent.class, listener);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Registration addValueChangeListener(Consumer<ValueChangeEvent<Folder>> listener) {
        return getEventBus().addListener(ValueChangeEvent.class, ((Consumer) listener));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Registration addItemSetChangeListener(Consumer<ItemSetChangeEvent<Folder>> listener) {
        return getEventBus().addListener(ItemSetChangeEvent.class, ((Consumer) listener));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Registration addSelectedItemChangeListener(Consumer<SelectedItemChangeEvent<Folder>> listener) {
        return getEventBus().addListener(SelectedItemChangeEvent.class, ((Consumer) listener));
    }

    @Override
    public boolean containsItem(Folder item) {
        return true;
    }

    @Override
    public boolean isInMemory() {
        return true;
    }

    @Override
    public Class<Folder> getType() {
        return getEntityMetaClass().getJavaClass();
    }

    @Override
    public BindingState getState() {
        return BindingState.ACTIVE;
    }

    protected EventBus getEventBus() {
        if (eventBus == null) {
            eventBus = new EventBus();
        }

        return eventBus;
    }

}

