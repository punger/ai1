package com.example.caesarandcleopatra;


import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import jakarta.ws.rs.WebApplicationException;
import jakarta.inject.Singleton;
import java.time.OffsetDateTime;

@Provider
@Singleton
public class CommonExceptionMapper implements ExceptionMapper<Throwable> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable ex) {
        int status;
        String message;

        if (ex instanceof WebApplicationException wae) {
            status = wae.getResponse().getStatus();
            message = wae.getMessage();
        } else if (ex instanceof IllegalArgumentException) {
            status = Response.Status.BAD_REQUEST.getStatusCode();
            message = ex.getMessage();
        } else {
            status = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
            message = "Unexpected server error.";
        }

        String json = String.format("""
            {
              "timestamp": "%s",
              "message": "%s",
              "status": %d,
              "path": "%s"
            }
            """,
            OffsetDateTime.now(),
            escape(message),
            status,
            uriInfo.getPath()
        );

        return Response.status(status)
                       .type(MediaType.APPLICATION_JSON)
                       .entity(json)
                       .build();
    }

    // Optional: escape quotes in message to avoid JSON breakage
    private String escape(String input) {
        return input == null ? "" : input.replace("\"", "\\\"");
    }
}
