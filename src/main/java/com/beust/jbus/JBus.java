package com.beust.jbus;

import com.beust.jbus.internal.Lists;
import com.beust.jbus.internal.Maps;
import com.beust.jbus.internal.Sets;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class JBus implements IBus {
  private boolean m_verbose = false;

  private Map<Class<?>, Set<Target>> m_subscribers = Maps.newHashMap();

  /* (non-Javadoc)
   * @see com.beust.jbus.IBus#register(java.lang.Object)
   */
  public void register(Object object) {
    p("Registering object " + object + " # methods:" + object.getClass().getMethods().length);
    for (Method m : object.getClass().getMethods()) {
      Subscriber s = m.getAnnotation(Subscriber.class);
      if (s != null) {
        p("Found @Subscribers on method:" + m);
        for (Class<?> type : m.getParameterTypes()) {
          p("Registering " + type + " with " + m);
          Set<Target> targetList = m_subscribers.get(type);
          if (targetList == null) {
            targetList = Sets.newHashSet();
            m_subscribers.put(type, targetList);
          }
          Target target = new Target(object, m);
          targetList.add(target);
        }
      }
    }
  }

  /* (non-Javadoc)
   * @see com.beust.jbus.IBus#unregister(java.lang.Object)
   */
  public void unregister(Object object) {
    for (Map.Entry<Class<?>, Set<Target>> set : m_subscribers.entrySet()) {
      Set<Target> targets = set.getValue();
      Set<Target> remove = Sets.newHashSet();
      for (Target t : targets) {
        // Note: use ==, not equals()
        if (t.getObject() == object) remove.add(t);
      }
      targets.removeAll(remove);
    }
  }

  private void p(String string) {
    if (m_verbose) {
      System.out.println("  [JBus] " + string);
    }
  }

  /* (non-Javadoc)
   * @see com.beust.jbus.IBus#post(java.lang.Object)
   */
  public void post(Object event) {
    post(event, new String[0]);
  }

//  public void post(PropertyChangeEvent event)
//  {
//    post(event, new String[] { event.getPropertyName() });
//  }

  /* (non-Javadoc)
   * @see com.beust.jbus.IBus#post(java.lang.Object, java.lang.String[])
   */
  public void post(Object event, String[] categories) {
    p("Posted:" + event);
    List<Target> targets = findTargets(event, categories);
    if (targets != null) {
      p("  Targets:" + targets);
      for (Target t : targets)
      {
        try {
          t.getMethod().invoke(t.getObject(), event);
        } catch (IllegalArgumentException e) {
          e.printStackTrace();
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        } catch (InvocationTargetException e) {
          e.printStackTrace();
        }
      }
    } else {
      p("No subscriber for event " + event);
    }
  }

  private List<Target> findTargets(Object event, String[] categories) {
    List<Target> result = Lists.newArrayList();

    // Find all the classes that either are equal or a subsclass of the event class
    for (Class<?> o : m_subscribers.keySet()) {
      Class<? extends Object> eventClass = event.getClass();
      if (o == event.getClass() || o.isAssignableFrom(eventClass)) {
        Set<Target> allTargets = m_subscribers.get(o);
        synchronized(allTargets) {
          Collection<? extends Target> filteredTargets = filterCategories(allTargets, categories);
          result.addAll(filteredTargets);
//          p("Event:" + event + " Targets:" + filteredTargets);
        }
      }
    }

    return result;
  }

  private Collection<? extends Target> filterCategories(Set<Target> list,
      String[] categories)
  {
    if (categories.length == 0) return list;

    List<Target> result = Lists.newArrayList();
    for (Target t : list) {
     String[] patterns = t.getCategoryPatterns();
     for (String pattern : patterns) {
       for (String category : categories) {
         p("Matching pattern " + pattern + " with " + category);
         if (Pattern.matches(pattern, category)) result.add(t);
       }
     }
    }

    return result;
  }

  public void setVerbose(boolean verbose) {
    m_verbose = verbose;
  }
}
