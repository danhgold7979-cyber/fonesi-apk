import java.util.Arrays;
import java.util.Collections;
import vn.fonesi.rebuild.model.ManagerModel.CatalogKey;
import vn.fonesi.rebuild.model.ManagerController;
import vn.fonesi.rebuild.model.ManagerModel;

public final class ManagerControllerTest {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("controller check " + checks);
    }
    private static final class FakeView implements ManagerController.View {
        ManagerController.Render state;
        int renders;
        @Override public void render(ManagerController.Render next) { state = next; renders++; }
    }
    public static void main(String[] args) {
        ManagerController controller = new ManagerController();
        check(!controller.current().active);
        check(controller.current().visible.isEmpty());
        check(!controller.filter(controller.current().token, "x", null));
        check(controller.beginRefresh(controller.current().token) == null);
        FakeView view = new FakeView();
        controller.attach(view);
        check(view.state.active);
        check(view.state.phase == ManagerModel.Phase.IDLE);
        check(view.state.totalCount == 0);
        check(!view.state.operationsEnabled());
        ManagerController.Token initial = view.state.token;
        check(controller.filter(initial, "name", null));
        check(view.state.query.equals("name"));
        check(!controller.filter(initial, "old", null));
        ManagerController.RefreshTicket interrupted = controller.beginRefresh(view.state.token);
        check(interrupted != null);
        check(view.state.phase == ManagerModel.Phase.LOADING);
        controller.detach();
        check(!view.state.active);
        check(view.state.visible.isEmpty());
        check(view.state.selected.isEmpty());
        check(view.state.totalCount == 0);
        check(view.state.query.isEmpty());
        check(!controller.publish(interrupted, null));
        check(!controller.fail(interrupted));
        check(!controller.selectVisible(initial, true));
        int detachedRenders = view.renders;
        controller.detach();
        check(view.renders == detachedRenders);
        FakeView resumed = new FakeView();
        controller.attach(resumed);
        check(resumed.state.active);
        check(resumed.state.phase == ManagerModel.Phase.FAILED);
        check(!controller.filter(initial, "old", null));
        ManagerController.RefreshTicket first = controller.beginRefresh(resumed.state.token);
        ManagerController.RefreshTicket second = controller.beginRefresh(resumed.state.token);
        check(first != second);
        check(!controller.fail(first));
        check(!controller.publish(first, Collections.emptyList()));
        ManagerModel.Entry row = new ManagerModel.Entry(new CatalogKey("unknown", "legacy-row-1"), "example.test",
                "Name", "group", ManagerModel.Status.QUARANTINED);
        check(controller.publish(second, Arrays.asList(row)));
        check(resumed.state.phase == ManagerModel.Phase.LOADED);
        check(resumed.state.visible.size() == 1);
        check(resumed.state.visible.get(0).status == ManagerModel.Status.QUARANTINED);
        check(!resumed.state.operationsEnabled());
        check(!controller.publish(second, Collections.emptyList()));
        check(!controller.fail(second));
        ManagerController.Token beforeSelection = resumed.state.token;
        check(controller.select(beforeSelection, row.key, true));
        check(resumed.state.selected.contains(row.key));
        check(!controller.select(beforeSelection, row.key, false));
        ManagerController.Token beforeFilter = resumed.state.token;
        check(controller.filter(beforeFilter, "absent", null));
        check(resumed.state.visible.isEmpty());
        check(resumed.state.totalCount == 1);
        check(resumed.state.selected.isEmpty());
        check(!controller.select(beforeFilter, row.key, true));
        check(controller.filter(resumed.state.token, "", null));
        check(controller.selectVisible(resumed.state.token, true));
        check(resumed.state.selected.size() == 1);
        ManagerController.Token oldEpoch = resumed.state.token;
        FakeView replacement = new FakeView();
        controller.attach(replacement); // repeated attach invalidates old view and selection
        check(!resumed.state.active);
        check(resumed.state.visible.isEmpty());
        check(replacement.state.selected.isEmpty());
        check(!controller.select(oldEpoch, row.key, true));
        check(replacement.state.visible.size() == 1);
        check(!replacement.state.operationsEnabled());
        ManagerController.RefreshTicket empty = controller.beginRefresh(replacement.state.token);
        check(controller.publish(empty, Collections.emptyList()));
        check(replacement.state.phase == ManagerModel.Phase.LOADED);
        check(replacement.state.totalCount == 0);
        ManagerController.RefreshTicket bad = controller.beginRefresh(replacement.state.token);
        try { controller.publish(bad, Arrays.asList(row, row)); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { checks++; }
        check(replacement.state.phase == ManagerModel.Phase.LOADING);
        check(controller.fail(bad));
        check(replacement.state.phase == ManagerModel.Phase.FAILED);
        check(replacement.state.totalCount == 0);
        check(!controller.fail(bad));
        ManagerController recursive = new ManagerController();
        recursive.attach(render -> {
            check(!recursive.filter(render.token, "recursive", null));
            check(!recursive.selectVisible(render.token, true));
            check(recursive.beginRefresh(render.token) == null);
            try { recursive.detach(); throw new AssertionError(); }
            catch (IllegalStateException expected) { checks++; }
        });
        check(recursive.current().active);
        check(recursive.current().query.isEmpty());
        ManagerController left = new ManagerController();
        ManagerController right = new ManagerController();
        left.attach(new FakeView());
        right.attach(new FakeView());
        check(!right.filter(left.current().token, "foreign", null));
        check(!right.selectVisible(left.current().token, true));
        check(right.beginRefresh(left.current().token) == null);
        check(right.current().query.isEmpty());
        ManagerController throwing = new ManagerController();
        int[] deliveries = {0};
        try {
            throwing.attach(render -> {
                if (++deliveries[0] == 1) throw new IllegalStateException("synthetic renderer failure");
            });
            throw new AssertionError();
        } catch (IllegalStateException expected) { checks++; }
        check(throwing.filter(throwing.current().token, "after-render-failure", null));
        check(deliveries[0] == 2);
        check(throwing.current().query.equals("after-render-failure"));
        check(!throwing.current().operationsEnabled());
        ManagerController composite = new ManagerController();
        FakeView compositeView = new FakeView(); composite.attach(compositeView);
        ManagerModel.Entry sameClone = new ManagerModel.Entry(new CatalogKey("other-engine", row.key.cloneId),
                "example.other", "Other", "", ManagerModel.Status.UNAVAILABLE);
        ManagerController.RefreshTicket catalog = composite.beginRefresh(compositeView.state.token);
        check(composite.publish(catalog,Arrays.asList(row,sameClone)));
        check(compositeView.state.visible.size() == 2);
        ManagerController.Token beforeCompositeSelection = compositeView.state.token;
        check(composite.select(beforeCompositeSelection,row.key,true));
        ManagerController.Render frozenComposite = compositeView.state;
        check(!composite.select(beforeCompositeSelection,sameClone.key,true));
        check(compositeView.state.selected.equals(Collections.singleton(row.key)));
        check(composite.select(compositeView.state.token,sameClone.key,true));
        check(compositeView.state.selected.size() == 2);
        check(frozenComposite.selected.equals(Collections.singleton(row.key)));
        check(!composite.select(right.current().token,row.key,false));
        ManagerController.Token oldCompositeEpoch = compositeView.state.token;
        composite.detach(); composite.attach(new FakeView());
        check(!composite.select(oldCompositeEpoch,sameClone.key,true));
        check(composite.current().selected.isEmpty());
        check(!composite.current().operationsEnabled());
        ManagerController.RefreshTicket compositeDuplicate = composite.beginRefresh(composite.current().token);
        ManagerModel.Entry payloadDrift = new ManagerModel.Entry(new CatalogKey(row.key.engineKey,row.key.cloneId),
                "different.package", "Changed", "other", ManagerModel.Status.UNVERIFIED);
        try { composite.publish(compositeDuplicate,Arrays.asList(row,payloadDrift)); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { checks++; }
        check(composite.current().phase == ManagerModel.Phase.LOADING);
        check(composite.current().totalCount == 0);
        check(composite.fail(compositeDuplicate));
        if (checks < 92) throw new AssertionError("test coverage missing");
        System.out.println("PASS " + checks + " controller checks; fake view only, no Android UI execution");
    }
}
