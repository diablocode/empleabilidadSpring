package mx.udg.cucea.empleabilidadspring.opportunity;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import mx.udg.cucea.empleabilidadspring.graph.FusekiGraphException;
import mx.udg.cucea.empleabilidadspring.graph.FusekiOfferRepository;
import mx.udg.cucea.empleabilidadspring.graph.GraphMappingException;
import mx.udg.cucea.empleabilidadspring.graph.OfferFreshness;
import mx.udg.cucea.empleabilidadspring.opportunity.OpportunityResponse.Page;

@Service
public class OpportunityService {

    static final int MAX_PAGE_SIZE = 50;
    static final int MAX_OFFSET = 10_000;

    private final FusekiOfferRepository repository;

    public OpportunityService(FusekiOfferRepository repository) {
        this.repository = repository;
    }

    public Page list(int offset, int limit, boolean currentOnly) {
        validatePage(offset, limit);
        try {
            List<OpportunityResponse> matches = repository.findAllOffers().stream()
                    .filter(offer -> !currentOnly || offer.freshness() == OfferFreshness.CURRENT)
                    .map(OpportunityResponse::from)
                    .toList();
            int end = Math.min(offset + limit, matches.size());
            List<OpportunityResponse> page = offset >= matches.size()
                    ? List.of()
                    : matches.subList(offset, end);
            return new Page(offset, limit, page.size(), end < matches.size(), page);
        } catch (FusekiGraphException exception) {
            throw new DataUnavailableException(exception);
        } catch (GraphMappingException exception) {
            throw new InvalidGraphException(exception);
        } catch (IllegalStateException exception) {
            throw new DataLimitException(exception);
        }
    }

    public OpportunityResponse get(String offerId) {
        UUID requestedId;
        try {
            requestedId = UUID.fromString(offerId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidRequestException("offerId must be a valid opportunity ID.");
        }
        try {
            return repository.findAllOffers().stream()
                    .filter(offer -> OpportunityResponse.publicId(offer).equals(requestedId.toString()))
                    .findFirst()
                    .map(OpportunityResponse::from)
                    .orElseThrow(NotFoundException::new);
        } catch (FusekiGraphException exception) {
            throw new DataUnavailableException(exception);
        } catch (GraphMappingException exception) {
            throw new InvalidGraphException(exception);
        } catch (IllegalStateException exception) {
            throw new DataLimitException(exception);
        }
    }

    private static void validatePage(int offset, int limit) {
        if (offset < 0 || offset > MAX_OFFSET) {
            throw new InvalidRequestException("offset must be between 0 and " + MAX_OFFSET + ".");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
    }

    public static final class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }

    public static final class NotFoundException extends RuntimeException {
    }

    public static final class DataUnavailableException extends RuntimeException {
        public DataUnavailableException(Throwable cause) {
            super(cause);
        }
    }

    public static final class InvalidGraphException extends RuntimeException {
        public InvalidGraphException(Throwable cause) {
            super(cause);
        }
    }

    public static final class DataLimitException extends RuntimeException {
        public DataLimitException(Throwable cause) {
            super(cause);
        }
    }
}
