/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * copy from linux_list.h
 */

#ifndef RMB_LIST_H_
#define RMB_LIST_H_



#include <stddef.h>

/*
 * Simple doubly linked list implementation.
 *
 * Some of the internal functions ("__xxx") are useful when
 * manipulating whole lists rather than single entries, as
 * sometimes we already know the next/prev entries and we can
 * generate better code by using them directly rather than
 * using the generic single-entry routines.
 */

typedef struct rmb_st_list {
  struct st_list *prev,*next;
  void *data;
} RMB_LIST;

struct rmb_list_head
{
	struct rmb_list_head *next, *prev;
};

#define RMB_LIST_HEAD_INIT(name) { &(name), &(name) }

#define RMB_LIST_HEAD(name) \
	struct rmb_list_head name = RMB_LIST_HEAD_INIT(name)

static inline void RMB_INIT_LIST_HEAD(struct rmb_list_head *list)
{
	list->next = list;
	list->prev = list;
}
/*
 * Insert a new entry between two known consecutive entries.
 *
 * This is only for internal list manipulation where we know
 * the prev/next entries already!
 */
static inline void __rmb_list_add(struct rmb_list_head *newNode,
			      struct rmb_list_head *prev,
			      struct rmb_list_head *next)
{
	next->prev = newNode;
	newNode->next = next;
	newNode->prev = prev;
	prev->next = newNode;
}

/**
 * list_add - add a new entry
 * @new: new entry to be added
 * @head: list head to add it after
 *
 * Insert a new entry after the specified head.
 * This is good for implementing stacks.
 */
static inline void rmb_list_add(struct rmb_list_head *newNode, struct rmb_list_head *head)
{
	__rmb_list_add(newNode, head, head->next);
}


/**
 * list_add_tail - add a new entry
 * @new: new entry to be added
 * @head: list head to add it before
 *
 * Insert a new entry before the specified head.
 * This is useful for implementing queues.
 */
static inline void rmb_list_add_tail(struct rmb_list_head *newNode, struct rmb_list_head *head)
{
	__rmb_list_add(newNode, head->prev, head);
}

/*
 * Delete a list entry by making the prev/next entries
 * point to each other.
 *
 * This is only for internal list manipulation where we know
 * the prev/next entries already!
 */
static inline void __rmb_list_del(struct rmb_list_head * prev, struct rmb_list_head * next)
{
	next->prev = prev;
	prev->next = next;
}

/**
 * list_del - deletes entry from list.
 * @entry: the element to delete from the list.
 * Note: list_empty() on entry does not return true after this, the entry is
 * in an undefined state.
 */
static inline void rmb_list_del(struct rmb_list_head *entry)
{
	__rmb_list_del(entry->prev, entry->next);
	entry->next = NULL;
	entry->prev = NULL;
}

/**
 * list_replace - replace old entry by new one
 * @old : the element to be replaced
 * @new : the new element to insert
 *
 * If @old was empty, it will be overwritten.
 */
static inline void rmb_list_replace(struct rmb_list_head *old,
				struct rmb_list_head *newNode)
{
	newNode->next = old->next;
	newNode->next->prev = newNode;
	newNode->prev = old->prev;
	newNode->prev->next = newNode;
}

static inline void rmb_list_replace_init(struct rmb_list_head *old,
					struct rmb_list_head *newNode)
{
	rmb_list_replace(old, newNode);
	RMB_INIT_LIST_HEAD(old);
}

/**
 * list_del_init - deletes entry from list and reinitialize it.
 * @entry: the element to delete from the list.
 */
static inline void rmb_list_del_init(struct rmb_list_head *entry)
{
	__rmb_list_del(entry->prev, entry->next);
	RMB_INIT_LIST_HEAD(entry);
}

/**
 * list_move - delete from one list and add as another's head
 * @list: the entry to move
 * @head: the head that will precede our entry
 */
static inline void rmb_list_move(struct rmb_list_head *list, struct rmb_list_head *head)
{
	__rmb_list_del(list->prev, list->next);
	rmb_list_add(list, head);
}

/**
 * list_move_tail - delete from one list and add as another's tail
 * @list: the entry to move
 * @head: the head that will follow our entry
 */
static inline void rmb_list_move_tail(struct rmb_list_head *list,
				  struct rmb_list_head *head)
{
	__rmb_list_del(list->prev, list->next);
	rmb_list_add_tail(list, head);
}

/**
 * list_is_last - tests whether @list is the last entry in list @head
 * @list: the entry to test
 * @head: the head of the list
 */
static inline int rmb_list_is_last(const struct rmb_list_head *list,
				const struct rmb_list_head *head)
{
	return list->next == head;
}

/**
 * list_empty - tests whether a list is empty
 * @head: the list to test.
 */
static inline int rmb_list_empty(const struct rmb_list_head *head)
{
	return head->next == head;
}

/**
 * list_empty_careful - tests whether a list is empty and not being modified
 * @head: the list to test
 *
 * Description:
 * tests whether a list is empty _and_ checks that no other CPU might be
 * in the process of modifying either member (next or prev)
 *
 * NOTE: using list_empty_careful() without synchronization
 * can only be safe if the only activity that can happen
 * to the list entry is list_del_init(). Eg. it cannot be used
 * if another CPU could re-list_add() it.
 */
static inline int rmb_list_empty_careful(const struct rmb_list_head *head)
{
	struct rmb_list_head *next = head->next;
	return (next == head) && (next == head->prev);
}

/**
 * list_is_singular - tests whether a list has just one entry.
 * @head: the list to test.
 */
static inline int rmb_list_is_singular(const struct rmb_list_head *head)
{
	return !rmb_list_empty(head) && (head->next == head->prev);
}

static inline void __rmb_list_cut_position(struct rmb_list_head *list,
		struct rmb_list_head *head, struct rmb_list_head *entry)
{
	struct rmb_list_head *new_first = entry->next;
	list->next = head->next;
	list->next->prev = list;
	list->prev = entry;
	entry->next = list;
	head->next = new_first;
	new_first->prev = head;
}

/**
 * list_cut_position - cut a list into two
 * @list: a new list to add all removed entries
 * @head: a list with entries
 * @entry: an entry within head, could be the head itself
 *	and if so we won't cut the list
 *
 * This helper moves the initial part of @head, up to and
 * including @entry, from @head to @list. You should
 * pass on @entry an element you know is on @head. @list
 * should be an empty list or a list you do not care about
 * losing its data.
 *
 */
static inline void rmb_list_cut_position(struct rmb_list_head *list,
		struct rmb_list_head *head, struct rmb_list_head *entry)
{
	if (rmb_list_empty(head))
		return;
	if (rmb_list_is_singular(head) &&
		(head->next != entry && head != entry))
		return;
	if (entry == head)
		RMB_INIT_LIST_HEAD(list);
	else
		__rmb_list_cut_position(list, head, entry);
}

static inline void __rmb_list_splice(const struct rmb_list_head *list,
				 struct rmb_list_head *prev,
				 struct rmb_list_head *next)
{
	struct rmb_list_head *first = list->next;
	struct rmb_list_head *last = list->prev;

	first->prev = prev;
	prev->next = first;

	last->next = next;
	next->prev = last;
}

/**
 * list_splice - join two lists, this is designed for stacks
 * @list: the new list to add.
 * @head: the place to add it in the first list.
 */
static inline void rmb_list_splice(const struct rmb_list_head *list,
				struct rmb_list_head *head)
{
	if (!rmb_list_empty(list))
		__rmb_list_splice(list, head, head->next);
}

/**
 * list_splice_tail - join two lists, each list being a queue
 * @list: the new list to add.
 * @head: the place to add it in the first list.
 */
static inline void rmb_list_splice_tail(struct rmb_list_head *list,
				struct rmb_list_head *head)
{
	if (!rmb_list_empty(list))
		__rmb_list_splice(list, head->prev, head);
}

/**
 * list_splice_init - join two lists and reinitialise the emptied list.
 * @list: the new list to add.
 * @head: the place to add it in the first list.
 *
 * The list at @list is reinitialised
 */
static inline void rmb_list_splice_init(struct rmb_list_head *list,
				    struct rmb_list_head *head)
{
	if (!rmb_list_empty(list)) {
		__rmb_list_splice(list, head, head->next);
		RMB_INIT_LIST_HEAD(list);
	}
}

/**
 * list_splice_tail_init - join two lists and reinitialise the emptied list
 * @list: the new list to add.
 * @head: the place to add it in the first list.
 *
 * Each of the lists is a queue.
 * The list at @list is reinitialised
 */
static inline void rmb_list_splice_tail_init(struct rmb_list_head *list,
					 struct rmb_list_head *head)
{
	if (!rmb_list_empty(list)) {
		__rmb_list_splice(list, head->prev, head);
		RMB_INIT_LIST_HEAD(list);
	}
}

/**
* container_of - cast a member of a structure out to the containing structure
* @ptr:     the pointer to the member.
* @type:     the type of the container struct this is embedded in.
* @member:     the name of the member within the struct.
*
*/

#define rmb_container_of(ptr, type, member) ({            \
        const typeof( ((type *)0)->member ) *__mptr = (ptr);    \
        (type *)( (char *)__mptr - offsetof(type,member) );})


/**
 * list_entry - get the struct for this entry
 * @ptr:	the &struct list_head pointer.
 * @type:	the type of the struct this is embedded in.
 * @member:	the name of the list_struct within the struct.
 */
#define rmb_list_entry(ptr, type, member) \
	rmb_container_of(ptr, type, member)

/**
 * list_first_entry - get the first element from a list
 * @ptr:	the list head to take the element from.
 * @type:	the type of the struct this is embedded in.
 * @member:	the name of the list_struct within the struct.
 *
 * Note, that list is expected to be not empty.
 */
#define rmb_list_first_entry(ptr, type, member) \
	rmb_list_entry((ptr)->next, type, member)

/**
 * list_for_each	-	iterate over a list
 * @pos:	the &struct list_head to use as a loop cursor.
 * @head:	the head for your list.
 */
#define rmb_list_for_each(pos, head) \
	for (pos = (head)->next;  pos != (head); \
        	pos = pos->next)

/**
 * __list_for_each	-	iterate over a list
 * @pos:	the &struct list_head to use as a loop cursor.
 * @head:	the head for your list.
 *
 * This variant differs from list_for_each() in that it's the
 * simplest possible list iteration code, no prefetching is done.
 * Use this for code that knows the list to be very short (empty
 * or 1 entry) most of the time.
 */
#define __rmb_list_for_each(pos, head) \
	for (pos = (head)->next; pos != (head); pos = pos->next)

/**
 * list_for_each_prev	-	iterate over a list backwards
 * @pos:	the &struct list_head to use as a loop cursor.
 * @head:	the head for your list.
 */
#define rmb_list_for_each_prev(pos, head) \
	for (pos = (head)->prev; pos != (head); \
        	pos = pos->prev)

/**
 * list_for_each_safe - iterate over a list safe against removal of list entry
 * @pos:	the &struct list_head to use as a loop cursor.
 * @n:		another &struct list_head to use as temporary storage
 * @head:	the head for your list.
 */
#define rmb_list_for_each_safe(pos, n, head) \
	for (pos = (head)->next, n = pos->next; pos != (head); \
		pos = n, n = pos->next)

/**
 * list_for_each_prev_safe - iterate over a list backwards safe against removal of list entry
 * @pos:	the &struct list_head to use as a loop cursor.
 * @n:		another &struct list_head to use as temporary storage
 * @head:	the head for your list.
 */
#define rmb_list_for_each_prev_safe(pos, n, head) \
	for (pos = (head)->prev, n = pos->prev; \
	             pos != (head); \
	     pos = n, n = pos->prev)

/**
 * list_for_each_entry	-	iterate over list of given type
 * @pos:	the type * to use as a loop cursor.
 * @head:	the head for your list.
 * @member:	the name of the list_struct within the struct.
 */
#define rmb_list_for_each_entry(pos, head, member)				\
	for (pos = rmb_list_entry((head)->next, typeof(*pos), member);	\
	     &pos->member != (head); 	\
	     pos = rmb_list_entry(pos->member.next, typeof(*pos), member))

/**
 * list_for_each_entry_reverse - iterate backwards over list of given type.
 * @pos:	the type * to use as a loop cursor.
 * @head:	the head for your list.
 * @member:	the name of the list_struct within the struct.
 */
#define rmb_list_for_each_entry_reverse(pos, head, member)			\
	for (pos = rmb_list_entry((head)->prev, typeof(*pos), member);	\
	      &pos->member != (head); 	\
	     pos = rmb_list_entry(pos->member.prev, typeof(*pos), member))

/**
 * list_prepare_entry - prepare a pos entry for use in list_for_each_entry_continue()
 * @pos:	the type * to use as a start point
 * @head:	the head of the list
 * @member:	the name of the list_struct within the struct.
 *
 * Prepares a pos entry for use as a start point in list_for_each_entry_continue().
 */
#define rmb_list_prepare_entry(pos, head, member) \
	((pos) ? : rmb_list_entry(head, typeof(*pos), member))

/**
 * list_for_each_entry_continue - continue iteration over list of given type
 * @pos:	the type * to use as a loop cursor.
 * @head:	the head for your list.
 * @member:	the name of the list_struct within the struct.
 *
 * Continue to iterate over list of given type, continuing after
 * the current position.
 */
#define rmb_list_for_each_entry_continue(pos, head, member) 		\
	for (pos = rmb_list_entry(pos->member.next, typeof(*pos), member);	\
	      &pos->member != (head);	\
	     pos = rmb_list_entry(pos->member.next, typeof(*pos), member))

/**
 * list_for_each_entry_continue_reverse - iterate backwards from the given point
 * @pos:	the type * to use as a loop cursor.
 * @head:	the head for your list.
 * @member:	the name of the list_struct within the struct.
 *
 * Start to iterate over list of given type backwards, continuing after
 * the current position.
 */
#define rmb_list_for_each_entry_continue_reverse(pos, head, member)		\
	for (pos = rmb_list_entry(pos->member.prev, typeof(*pos), member);	\
	     &pos->member != (head);	\
	     pos = rmb_list_entry(pos->member.prev, typeof(*pos), member))

/**
 * list_for_each_entry_from - iterate over list of given type from the current point
 * @pos:	the type * to use as a loop cursor.
 * @head:	the head for your list.
 * @member:	the name of the list_struct within the struct.
 *
 * Iterate over list of given type, continuing from current position.
 */
#define rmb_list_for_each_entry_from(pos, head, member) 			\
	for (;  &pos->member != (head);	\
	     pos = rmb_list_entry(pos->member.next, typeof(*pos), member))

/**
 * list_for_each_entry_safe - iterate over list of given type safe against removal of list entry
 * @pos:	the type * to use as a loop cursor.
 * @n:		another type * to use as temporary storage
 * @head:	the head for your list.
 * @member:	the name of the list_struct within the struct.
 */
#define rmb_list_for_each_entry_safe(pos, n, head, member)			\
	for (pos = rmb_list_entry((head)->next, typeof(*pos), member),	\
		n = rmb_list_entry(pos->member.next, typeof(*pos), member);	\
	     &pos->member != (head); 					\
	     pos = n, n = rmb_list_entry(n->member.next, typeof(*n), member))

/**
 * list_for_each_entry_safe_continue
 * @pos:	the type * to use as a loop cursor.
 * @n:		another type * to use as temporary storage
 * @head:	the head for your list.
 * @member:	the name of the list_struct within the struct.
 *
 * Iterate over list of given type, continuing after current point,
 * safe against removal of list entry.
 */
#define rmb_list_for_each_entry_safe_continue(pos, n, head, member) 		\
	for (pos = rmb_list_entry(pos->member.next, typeof(*pos), member), 		\
		n = rmb_list_entry(pos->member.next, typeof(*pos), member);		\
	     &pos->member != (head);						\
	     pos = n, n = rmb_list_entry(n->member.next, typeof(*n), member))

/**
 * list_for_each_entry_safe_from
 * @pos:	the type * to use as a loop cursor.
 * @n:		another type * to use as temporary storage
 * @head:	the head for your list.
 * @member:	the name of the list_struct within the struct.
 *
 * Iterate over list of given type from current point, safe against
 * removal of list entry.
 */
#define rmb_list_for_each_entry_safe_from(pos, n, head, member) 			\
	for (n = rmb_list_entry(pos->member.next, typeof(*pos), member);		\
	     &pos->member != (head);						\
	     pos = n, n = rmb_list_entry(n->member.next, typeof(*n), member))

/**
 * list_for_each_entry_safe_reverse
 * @pos:	the type * to use as a loop cursor.
 * @n:		another type * to use as temporary storage
 * @head:	the head for your list.
 * @member:	the name of the list_struct within the struct.
 *
 * Iterate backwards over list of given type, safe against removal
 * of list entry.
 */
#define rmb_list_for_each_entry_safe_reverse(pos, n, head, member)		\
	for (pos = rmb_list_entry((head)->prev, typeof(*pos), member),	\
		n = rmb_list_entry(pos->member.prev, typeof(*pos), member);	\
	     &pos->member != (head); 					\
	     pos = n, n = rmb_list_entry(n->member.prev, typeof(*n), member))


#endif /* RMB_LIST_H_ */
