#include <argp.h> // parse command-line arguments
#include "ged_ebp.h"

static char doc[] = "Converts GEDCOM 5.5.1 to 7.0 (without validation)";
static char args_doc[] = "input.ged output.ged";
struct arguments {
    char *from;
    char *to;
    int overwrite;
};

static struct argp_option options[] = {
    {"from", 'f', "FILE", 0, "Read from file instead of standard input (defaults to first argument)"},
    {"to", 't', "FILE", 0, "Write to file instead of standard input (defaults to second argument)"},
    {"overwrite", 'o', 0, 0, "Overwrite output file if it already exists"},
    {"xreficase", 'x', 0, 0, "Compare pointers case-insensitively"},
    {"fewphrase", 'p', 0, 0, "Omit phrases when an applicable enumeration is found"},
{0}};

static error_t parse_opt(int key, char *arg, struct argp_state *state) {
    struct arguments *args = state->input;
    switch(key) {
        case 'f': args->from = *arg ? arg : NULL; break;
        case 't': args->to = *arg ? arg : NULL; break;
        case 'o': args->overwrite = 1; break;
        case 'x': ged_xref_case_insensitive = 1; break;
        case 'p': ged_few_phrases = 1; break;
        case ARGP_KEY_ARG:
            if (state->arg_num == 0 && !args->from) args->from = arg;
            else if (state->arg_num == 1 && !args->to) args->to = arg;
            else argp_usage(state);
            break;
        case ARGP_KEY_END:
            break;
        default:
            return ARGP_ERR_UNKNOWN;
    }
    return 0;
}

static struct argp parser = { options, parse_opt, args_doc, doc };

/**
 * Simple command-line wrapper.
 * Run with no arguments to see documentation.
 */
int main(int argc, char *argv[]) {
    struct arguments args;
    args.from = args.to = 0;
    args.overwrite = ged_xref_case_insensitive = ged_few_phrases = 0;
    argp_parse(&parser, argc, argv, 0, 0, &args);
    
    FILE *in = args.from ? fopen(args.from, "rb") : stdin;
    if (!in) {
        fprintf(stderr, "ERROR: unable to open %s for reading\n", args.from);
        return 2;
    }
    
    FILE *out = args.to ? fopen(args.to, args.overwrite ? "wb" : "wxb") : stdout;
    if (!out) {
        if (args.overwrite) {
            fprintf(stderr, "ERROR: unable to open %s for writing\n", argv[2]);
        } else {
            fprintf(stderr, "ERROR: unable to create %s for writing\n  (did you forget to use `--overwrite`?)\n", argv[2]);
        }
        return 3;
    }

    ged551to700(in, out);
    return 0;
}
