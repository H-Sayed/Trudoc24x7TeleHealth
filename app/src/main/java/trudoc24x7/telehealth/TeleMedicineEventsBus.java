package trudoc24x7.telehealth;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

public class TeleMedicineEventsBus {

    private static TeleMedicineEventsBus instance;

    private PublishSubject<Object> subject = PublishSubject.create();

    public static TeleMedicineEventsBus getInstance() {
        if (instance == null) {
            instance = new TeleMedicineEventsBus();
        }
        return instance;
    }


    public void sendEvent(Object object) {
        subject.onNext(object);
    }

    public Observable<Object> getBus() {
        return subject;
    }
}
