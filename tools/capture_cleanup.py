"""Bound cleanup without importing Frida, so timeout behavior is testable."""
import threading


def detach_bounded(session, timeout=5):
    done = threading.Event()
    outcome = []

    def detach():
        try:
            session.detach()
        except Exception as error:
            outcome.append(type(error).__name__)
        finally:
            done.set()

    threading.Thread(target=detach, daemon=True).start()
    return done.wait(timeout), outcome
