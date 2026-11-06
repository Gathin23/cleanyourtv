package io.github.vitorcamillo.wolfgames;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** User-confirmed, revision-checked session actions. No background polling or implicit quit. */
final class WolfSessionMenu {
    interface Listener {void launch(String appId,String appName);void focusShelf();void sessionState(WolfSession session);}
    interface Factory {WolfControl open() throws WolfControlClient.ControlException;}
    private interface Operation {WolfSession run(WolfControl control) throws WolfControlClient.ControlException;}
    private interface Result {void accept(WolfSession session);}
    private final Activity activity;
    private final Listener listener;
    private final Factory factory;
    private final ExecutorService executor;
    private final Handler main=new Handler(Looper.getMainLooper());
    private AlertDialog dialog;
    private boolean resumed,destroyed,busy,refreshPending;
    private int generation;

    WolfSessionMenu(Activity activity,String hostUuid,Listener listener) {
        this(activity,listener,()-> {
            // No wolf-control.json: no session control, so Moonlight launches directly.
            WolfControlClient control=WolfControlClient.load(new File(activity.getFilesDir(),"wolf-control.json"));
            if(control==null)throw new WolfControlClient.ControlException("config_error");
            if(!control.controls(hostUuid))throw new WolfControlClient.ControlException("config_error");
            return control;
        },new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1)));
    }
    WolfSessionMenu(Activity activity,Listener listener,Factory factory,ExecutorService executor) {
        this.activity=activity;this.listener=listener;this.factory=factory;this.executor=executor;
    }
    void resume() {
        if(destroyed || resumed)return;
        resumed=true;
        refreshSession();
    }
    private void refreshSession() {
        if(busy) {if(resumed && !destroyed)refreshPending=true;return;}
        if(!resumed || destroyed)return;
        busy=true;int ticket=++generation;
        try {executor.execute(()-> {
            WolfSession session=null;
            try {session=factory.open().getSession();}catch(WolfControlClient.ControlException|RuntimeException ignored) {}
            WolfSession ready=session;
            main.post(()-> {
                busy=false;
                if(refreshPending) {refreshPending=false;if(resumed && !destroyed)refreshSession();}
                if(resumed && !destroyed && ticket==generation)listener.sessionState(ready);
            });
        });}catch(RejectedExecutionException ignored){busy=false;}
    }
    void pause() {resumed=false;generation++;dismiss();}
    void close() {destroyed=true;refreshPending=false;pause();executor.shutdownNow();}
    void open(String appId,String appName) {
        try {WolfSession.validateId(appId);MoonlightCatalog.label(appName);}
        catch(IllegalArgumentException error){showError("invalid_request",null);return;}
        Runnable retry=()->open(appId,appName);
        request("Consultando jogo em andamento…",WolfControl::getSession,session-> {
            listener.sessionState(session);
            switch(session.forTarget(appId)) {
                case LAUNCH: launch(appId,appName);break;
                case RESUME_MENU: activeMenu(session);break;
                case CONFIRM_SWITCH: confirmStop(session,appId,appName);break;
            }
        },retry);
    }
    void showCurrent() {
        request("Consultando jogo em andamento…",WolfControl::getSession,session-> {
            listener.sessionState(session);
            if(session.active)activeMenu(session);
            else {toast("Nenhum jogo está aberto no Wolf.");listener.focusShelf();}
        },this::showCurrent);
    }
    private void activeMenu(WolfSession session) {
        dismiss();
        String[] choices=new String[]{"Retomar jogo","Encerrar jogo","Cancelar"};
        dialog=new AlertDialog.Builder(activity).setTitle(session.appName)
                .setItems(choices,(window,which)-> {
                    if(which==0)resumeSession(session);
                    else if(which==1)confirmStop(session,null,null);
                    else listener.focusShelf();
                }).setOnCancelListener(window->listener.focusShelf()).show();
    }
    private void resumeSession(WolfSession expected) {
        request("Conferindo a sessão…",WolfControl::getSession,current-> {
            if(!current.active || !current.appId.equals(expected.appId) || !current.revision.equals(expected.revision)) {
                showError("session_changed",this::showCurrent);return;
            }
            launch(current.appId,current.appName);
        },this::showCurrent);
    }
    private void confirmStop(WolfSession expected,String targetId,String targetName) {
        dismiss();
        String message=targetId==null?"Encerrar "+expected.appName+"?":"Encerrar "+expected.appName+" e abrir "+targetName+"?";
        dialog=new AlertDialog.Builder(activity).setTitle(targetId==null?"Encerrar jogo?":"Trocar de jogo?")
                .setMessage(message+"\n\nO progresso não salvo pode ser perdido.")
                .setPositiveButton(targetId==null?"Encerrar jogo":"Encerrar e trocar",(window,which)-> {
                    Runnable retry=targetId==null?this::showCurrent:()->open(targetId,targetName);
                    request("Encerrando o jogo…",control->control.stop(expected),current-> {
                        if(current.active){showError("protocol_error",retry);return;}
                        listener.sessionState(current);
                        if(targetId!=null)launch(targetId,targetName);
                        else {toast("Jogo encerrado.");listener.focusShelf();}
                    },retry,false);
                }).setNegativeButton("Cancelar",(window,which)->listener.focusShelf())
                .setOnCancelListener(window->listener.focusShelf()).show();
    }
    private void launch(String id,String name) {
        if(!resumed || destroyed)return;
        dismiss();
        listener.launch(id,name);
    }
    private void request(String message,Operation operation,Result result,Runnable retry) {
        request(message,operation,result,retry,true);
    }
    private void request(String message,Operation operation,Result result,Runnable retry,boolean cancellable) {
        if(!resumed || destroyed)return;
        if(busy){toast("Aguarde a operação em andamento.");return;}
        busy=true;int ticket=++generation;dismiss();
        dialog=new AlertDialog.Builder(activity).setMessage(message).setCancelable(cancellable).setOnCancelListener(window-> {
            generation++;listener.focusShelf(); // Invalidate result; busy remains true until I/O actually ends.
        }).show();
        try {
            executor.execute(()-> {
                WolfSession session=null;String failure=null;
                try {session=operation.run(factory.open());}
                catch(WolfControlClient.ControlException error){failure=error.code;}
                catch(RuntimeException error){failure="protocol_error";}
                WolfSession ready=session;String code=failure;
                main.post(()-> {
                    busy=false;
                    if(refreshPending) {refreshPending=false;main.post(this::refreshSession);} // after this result is handled
                    if(destroyed || !resumed || ticket!=generation)return;
                    dismiss();
                    if(code!=null){Log.w("WolfSessionControl","operation_failed code="+safeCode(code));showError(code,retry);}
                    else if(ready==null)showError("protocol_error",retry);
                    else result.accept(ready);
                });
            });
        } catch(RejectedExecutionException error){busy=false;dismiss();showError("busy",retry);}
    }
    static String safeCode(String code) {
        switch(code) {
            case "network_error":case "tls_error":case "unauthorized":case "config_error":case "protocol_error":
            case "session_changed":case "busy":case "stop_timeout":case "upstream_unavailable":case "invalid_request":return code;
            default:return "protocol_error";
        }
    }
    private void showError(String code,Runnable retry) {
        if(!resumed || destroyed)return;
        String message;
        switch(safeCode(code)) {
            case "session_changed":message="O jogo em andamento mudou. Consulte novamente antes de encerrar.";break;
            case "busy":message="O jogo ainda está iniciando ou encerrando. Aguarde e tente novamente.";break;
            case "stop_timeout":message="O jogo ainda não terminou de encerrar. O próximo jogo não foi aberto.";break;
            case "config_error":case "unauthorized":case "tls_error":message="Não foi possível validar o controle do Wolf. A configuração precisa ser conferida.";break;
            case "network_error":case "upstream_unavailable":message="O controle do Wolf está indisponível. Confira se o PC está ligado e tente novamente.";break;
            default:message="Não foi possível confirmar o estado do jogo. Nenhuma troca foi iniciada.";
        }
        dismiss();AlertDialog.Builder builder=new AlertDialog.Builder(activity).setTitle("Não foi possível concluir")
                .setMessage(message).setNegativeButton("Fechar",(window,which)->listener.focusShelf())
                .setOnCancelListener(window->listener.focusShelf());
        if(retry!=null)builder.setPositiveButton("Tentar novamente",(window,which)->retry.run());
        dialog=builder.show();
    }
    private void dismiss() {if(dialog!=null){dialog.dismiss();dialog=null;}}
    private void toast(String text) {Toast.makeText(activity,text,Toast.LENGTH_SHORT).show();}
}
