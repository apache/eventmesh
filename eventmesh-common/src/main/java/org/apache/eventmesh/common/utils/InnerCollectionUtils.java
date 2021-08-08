package org.apache.eventmesh.common.utils;

import com.google.common.base.Preconditions;
import org.apache.commons.collections4.CollectionUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class InnerCollectionUtils {

    /**
     * transform origin collection to ArrayList
     *
     * @param collection        origin collection
     * @param transformFunction transform function
     * @param <T>               origin Type
     * @param <R>               target Type
     * @return array list
     */
    public static <T, R> List<R> collectionToArrayList(Collection<T> collection, @Nonnull Function<T, R> transformFunction) {
        Preconditions.checkNotNull(transformFunction, "transform function cannot be null");
        if (CollectionUtils.isEmpty(collection)) {
            return new ArrayList<>();
        }
        return collection.stream()
                .map(transformFunction).collect(Collectors.toList());
    }
}
